package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Lst, Null, Obj, Value}
import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model.RequestContext
import com.hypertino.facade.raml.{Field, FieldAnnotationWithFilter, RamlConfiguration, TypeDefinition}
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, DynamicResponse, StandardResponse}
import monix.eval.Task
import monix.execution.Scheduler
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

class RequestFieldFilterAdapter(val typeDef: TypeDefinition,
                                protected val expressionEvaluator: ExpressionEvaluator,
                                protected implicit val injector: Injector,
                                protected implicit val scheduler: Scheduler) // todo: remove ec: ExecutionContext
  extends RequestFilter with FieldFilterBase with Injectable {

  protected lazy val ramlConfiguration = inject[RamlConfiguration]

  def apply(contextWithRequest: RequestContext)
           (implicit ec: ExecutionContext): Future[RequestContext] = {
    filterBody(contextWithRequest.request.body.content, contextWithRequest, FieldFilterStageRequest).map { body ⇒
      contextWithRequest.copy(
        request = contextWithRequest.request.copy(body = DynamicBody(body))
      )
    }.runAsync
  }
}

class ResponseFieldFilterAdapter(val typeDef: TypeDefinition,
                                 protected val expressionEvaluator: ExpressionEvaluator,
                                 protected implicit val injector: Injector,
                                 protected implicit val scheduler: Scheduler)
  extends ResponseFilter with FieldFilterBase with Injectable{

  protected lazy val ramlConfiguration = inject[RamlConfiguration]

  def apply(contextWithRequest: RequestContext, response: DynamicResponse)
           (implicit ec: ExecutionContext): Future[DynamicResponse] = {
    filterBody(response.body.content, contextWithRequest, FieldFilterStageResponse).map { body ⇒
      StandardResponse(body = DynamicBody(body), response.headers)
        .asInstanceOf[DynamicResponse]
    }.runAsync
  }
}


class EventFieldFilterAdapter(val typeDef: TypeDefinition,
                              protected val expressionEvaluator: ExpressionEvaluator,
                              protected implicit val injector: Injector,
                              protected implicit val scheduler: Scheduler)
  extends EventFilter with FieldFilterBase with Injectable {

  protected lazy val ramlConfiguration = inject[RamlConfiguration]

  def apply(contextWithRequest: RequestContext, event: DynamicRequest)
           (implicit ec: ExecutionContext): Future[DynamicRequest] = {
    filterBody(event.body.content, contextWithRequest, FieldFilterStageEvent).map { body ⇒
      DynamicRequest(DynamicBody(body), contextWithRequest.request.headers)
    }.runAsync
  }
}

class FieldFilterAdapterFactory(protected val predicateEvaluator: ExpressionEvaluator,
                                protected implicit val injector: Injector,
                                protected implicit val scheduler: Scheduler) extends Injectable {
  def createFilters(typeDef: TypeDefinition): SimpleFilterChain = {
    SimpleFilterChain(
      requestFilters = Seq(new RequestFieldFilterAdapter(typeDef, predicateEvaluator, injector, scheduler)),
      responseFilters = Seq(new ResponseFieldFilterAdapter(typeDef, predicateEvaluator, injector, scheduler)),
      eventFilters = Seq(new EventFieldFilterAdapter(typeDef, predicateEvaluator, injector, scheduler))
    )
  }
}

trait FieldFilterBase {
  protected def typeDef: TypeDefinition
  protected implicit def scheduler: Scheduler
  protected def expressionEvaluator: ExpressionEvaluator

  protected def filterBody(body: Value, requestContext: RequestContext, stage: FieldFilterStage): Task[Value] = {
    recursiveFilterValue(body, body, requestContext, typeDef, Seq.empty, stage)
  }

  protected def recursiveFilterValue(rootValue: Value,
                                     value: Value,
                                     requestContext: RequestContext,
                                     typeDef: TypeDefinition,
                                     fieldPath: Seq[String], stage: FieldFilterStage): Task[Value] = {
    if (typeDef.isCollection) {
      val tc = typeDef.copy(isCollection = false)
      Task.gather {
        value.toSeq.map { li ⇒
          recursiveFilterValue(rootValue, li, requestContext, tc, fieldPath, stage)
        }
      }.map(Lst(_))
    } else {
      val m = value.toMap
      val updateExistingFields = m.map { case (k, v) ⇒
        typeDef
          .fields
          .get(k)
          .map(filterMatching(_, rootValue, Some(v), value, fieldPath, requestContext, stage))
          .getOrElse {
            Task.now(k → Some(v))
          }
      }.toSeq

      val newFields = typeDef
        .fields
        .filterNot(f ⇒ m.contains(f._1))
        .map { case (_, field) ⇒
          filterMatching(field, rootValue, None, value, fieldPath, requestContext, stage)
        }
        .toSeq

      Task.gather(updateExistingFields ++ newFields).flatMap { res ⇒
        Task.gather {
          res.map {
            case (k, Some(v)) ⇒
              typeDef
                .fields
                .get(k)
                .flatMap { field ⇒
                  field
                    .typeDefinition
                    .map { innerTypeDef ⇒
                      recursiveFilterValue(rootValue, v, requestContext, innerTypeDef, fieldPath :+ field.fieldName, stage)
                        .map(vv ⇒ Some(k → vv))
                    }
                }.getOrElse {
                Task.now(Some(k → v))
              }

            case (_, None) ⇒
              Task.now(None)
          }
        } map { inner ⇒
          Obj.from(inner.flatten: _*)
        }
      }
    }
  }

  protected def filterMatching(field: Field,
                               rootValue: Value,
                               value: Option[Value],
                               siblings: Value,
                               parentFieldPath: Seq[String],
                               requestContext: RequestContext,
                               stage: FieldFilterStage): Task[(String, Option[Value])] = {
    val extraContext = Obj.from(
      "this" → siblings,
      "root" → rootValue,
      "stage" → stage.stringValue
    )
    field
      .annotations
      .filter {
        fa ⇒
          fa.annotation.stages.contains(stage) &&
          fa.annotation.predicate.forall(expressionEvaluator.evaluatePredicate(requestContext, extraContext, _))
      }
      .foldLeft(Task.now(field.fieldName → value)) { case (lastValueTask, a) ⇒
        lastValueTask.flatMap( lastValue ⇒
          a.filter(FieldFilterContext(parentFieldPath :+ field.fieldName, lastValue._2, field, extraContext, requestContext, stage))
            .map(field.fieldName → _)
        )
      }
  }
}




