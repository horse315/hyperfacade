package com.hypertino.facade.filter.http

import com.hypertino.binders.value.{Null, Obj, Text, Value}
import com.hypertino.facade.apiref.auth.{Validation, ValidationResult, ValidationsPost}
import com.hypertino.facade.apiref.user.UsersGet
import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model._
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model._
import monix.eval.Task
import monix.execution.Scheduler
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

private[http] case class TaskResult(headerName: String, headerValue: Value, contextValue: Value)

class AuthenticationRequestFilter(hyperbus: Hyperbus,
                                  protected val expressionEvaluator: ExpressionEvaluator,
                                  protected implicit val scheduler: Scheduler) extends RequestFilter {
  protected val log = LoggerFactory.getLogger(getClass)

  override def apply(requestContext: RequestContext)
                    (implicit ec: ExecutionContext): Future[RequestContext] = {
    implicit val mcx = requestContext.request

    Task.gatherUnordered(Seq(authenticationTask(requestContext), privelegeAuthorizationTask(requestContext))).map { results ⇒
      val removeHeaders = results.filter(_.headerValue.isEmpty).map(_.headerName)
      val addHeaders = RequestHeaders(HeadersMap(
        results.filter(_.headerValue.isDefined).map(t ⇒ t.headerName → t.headerValue) :_*
      ))
      val contextObj = results.foldLeft[Value](Null) { (current: Value, t) ⇒
        current + t.contextValue
      }

      requestContext.copy(
        request = requestContext.request.copy(
          headers = RequestHeaders(HeadersMap(requestContext
            .request
            .headers
            .toSeq
            .filterNot(_._1 == removeHeaders) ++ addHeaders :_*))
        ),
        contextStorage = requestContext.contextStorage + contextObj
      )
    }.runAsync
  }

  private def validateCredentials(credentials: String)(implicit mcx: MessagingContext): Task[ValidationResult]= {
    getAuthServiceNameFromCredentials(credentials) map { authServiceName ⇒
      val v = ValidationsPost(Validation(credentials))
      val authRequest = v.copy(headers =
        Headers
          .builder
          .++=(v.headers)
          .withHRL(v.headers.hrl.copy(location = authServiceName))
          .requestHeaders()
      )

      hyperbus.ask(authRequest).map(_.body)
    } getOrElse {
      Task.raiseError(
        BadRequest(ErrorBody("unsupported-authorization-scheme", Some(s"Authorization scheme doesn't have first part!")))
      )
    }
  }

  private def authenticationTask(implicit requestContext: RequestContext): Task[TaskResult] = {
    requestContext.originalHeaders.get(FacadeHeaders.AUTHORIZATION) match {
      case Some(Text(credentials)) ⇒
        validateCredentials(credentials)
          .flatMap { validation ⇒
            val userRequest = UsersGet($query = validation.identityKeys)
            hyperbus
              .ask(userRequest)
              .flatMap { users ⇒
                val userCollection = users.body.content.toSeq
                if (userCollection.isEmpty) {
                  Task.raiseError(Unauthorized(ErrorBody("user-not-exists", Some(s"Credentials are valid, but user doesn't exists"))))
                }
                else if (userCollection.tail.nonEmpty) {
                  Task.raiseError(Unauthorized(ErrorBody("multiple-users-found", Some(s"Credentials are valid, but multiple users correspond to the identity keys"))))
                }
                else {
                  Task.eval {
                    val user = userCollection.head
                    val userId = user.user_id
                    TaskResult("Authorization-Result", Obj.from("user_id" → userId), Obj.from(
                      ContextStorage.USER → user
                    ))
                  }
                }
              }
            //validation.body.
          }

      case _ ⇒
        Task.now(TaskResult("Authorization-Result", Null, Null))
    }
  }

  private def privelegeAuthorizationTask(implicit requestContext: RequestContext): Task[TaskResult] = {
    requestContext.originalHeaders.get(FacadeHeaders.PRIVILEGE_AUTHORIZATION) match {
      case Some(Text(credentials)) ⇒
        validateCredentials(credentials).map { v ⇒
          TaskResult("Privilege-Authorization-Result", Obj.from(
            "identity_keys" → v.identityKeys,
            "extra" → v.extra
          ), Null)
        }
      case _ ⇒
        Task.now(TaskResult("Privilege-Authorization-Result", Null, Null))
    }
  }
  private def getAuthServiceNameFromCredentials(credentials: String): Option[String] = {
    val c = credentials.trim
    val i = c.indexOf(' ')
    if (i > 0) Some {
      ValidationsPost.location.replace("hb://auth/", "hb://auth-" + c.substring(0, i).trim.toLowerCase + "/")
    }
    else {
      None
    }
  }
}
