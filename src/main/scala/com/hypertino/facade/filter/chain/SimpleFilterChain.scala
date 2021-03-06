package com.hypertino.facade.filter.chain

import com.hypertino.facade.filter.model._
import com.hypertino.facade.model._
import com.hypertino.hyperbus.model.{DynamicRequest, DynamicResponse}


case class SimpleFilterChain(
                              requestFilters: Seq[RequestFilter] = Seq.empty,
                              responseFilters: Seq[ResponseFilter] = Seq.empty,
                              eventFilters: Seq[EventFilter] = Seq.empty
                       ) extends FilterChain {

  override def findRequestFilters(contextWithRequest: RequestContext): Seq[RequestFilter] = requestFilters
  override def findResponseFilters(context: RequestContext, response: DynamicResponse): Seq[ResponseFilter] = responseFilters
  override def findEventFilters(context: RequestContext, event: DynamicRequest): Seq[EventFilter] = eventFilters

  def ++(other: SimpleFilterChain): SimpleFilterChain = {
    SimpleFilterChain(
      requestFilters ++ other.requestFilters,
      other.responseFilters ++ responseFilters, // <- reverse order!
      other.eventFilters ++ eventFilters        // <- reverse order!
    )
  }
}

object SimpleFilterChain {
  val empty = SimpleFilterChain()
  def apply(i: Iterable[SimpleFilterChain]): SimpleFilterChain = {
    i.foldLeft(SimpleFilterChain.empty) { (result, current) ⇒
      result ++ current
    }
  }
}
