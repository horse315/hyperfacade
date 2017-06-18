package com.hypertino.facade.filter.raml

import com.hypertino.facade.filter.chain.{FilterChain, SimpleFilterChain}
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.PredicateEvaluator
import com.hypertino.facade.model.{ContextStorage, ContextWithRequest}
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

class AuthorizeRequestFilter extends RequestFilter {

  override def apply(contextWithRequest: ContextWithRequest)
                    (implicit ec: ExecutionContext): Future[ContextWithRequest] = {
    Future {
      val updatedContextStorage = contextWithRequest.contextStorage + (ContextStorage.IS_AUTHORIZED → true)
      contextWithRequest.copy (
        contextStorage = updatedContextStorage
      )
    }
  }
}

class AuthorizeFilterFactory(implicit inj: Injector) extends RamlFilterFactory with Injectable {
  val log = LoggerFactory.getLogger(getClass)
  val predicateEvaluator = inject[PredicateEvaluator]

  override def createFilters(target: RamlTarget): SimpleFilterChain = {
    target match {
      case TargetResource(_, _) ⇒
        SimpleFilterChain(
          requestFilters = Seq(new AuthorizeRequestFilter),
          responseFilters = Seq.empty,
          eventFilters = Seq.empty
        )

      case TargetMethod(_, _, _) ⇒
        SimpleFilterChain(
          requestFilters = Seq(new AuthorizeRequestFilter),
          responseFilters = Seq.empty,
          eventFilters = Seq.empty
        )

      case unknownTarget ⇒
        log.warn(s"Annotation (authorize) is not supported for target $unknownTarget. Empty filter chain will be created")
        FilterChain.empty
    }
  }
}