package eu.inn.facade.filter

import eu.inn.binders.value.{Null, ObjV}
import eu.inn.facade.MockContext
import eu.inn.facade.filter.chain.{FilterChain, RamlFilterChain}
import eu.inn.facade.filter.raml.{EnrichRequestFilter, EventPrivateFilter, RequestPrivateFilter, ResponsePrivateFilter}
import eu.inn.facade.model.{FacadeRequest, _}
import eu.inn.facade.modules.Injectors
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.{FreeSpec, Matchers}
import scaldi.Injectable

// todo: important to test when specific != formatted!
// + integrated test with filter lookup when specific != formatted!

class RamlFilterChainTest extends FreeSpec with Matchers with Injectable with MockContext {
  implicit val injector = Injectors()

  val filterChain = inject [FilterChain].asInstanceOf[RamlFilterChain]

  "FilterChainRamlFactory " - {
    "resource filter chain" in {
      val request = FacadeRequest(Uri("/private"), "get", Map.empty, Null)
      val context = mockContext(request)
      val filters = filterChain.findRequestFilters(context, request)
      filters.length should equal(1)
      filters.head shouldBe a[RequestPrivateFilter]
    }

    "annotation based filter chain" in {
      val request = FacadeRequest(Uri("/status/test-service"), "get", Map.empty, Null)
      val context = mockContext(request)
      val filters = filterChain.findRequestFilters(context, request)
      filters.length should equal(1)
      filters.head shouldBe a[EnrichRequestFilter]
    }

    "trait and annotation based filter chain" in {
      val request = FacadeRequest(Uri("/users/{userId}", Map("userId" → "100500")), "get", Map.empty, Null)
      val response = FacadeResponse(200, Map.empty, Null)
      val context = mockContext(request.copy(uri=Uri(request.uri.formatted))).prepare(request)
      val filters = filterChain.findResponseFilters(context, response)

      filters.head shouldBe a[ResponsePrivateFilter]
      filters.tail.head shouldBe a[NoOpFilter]
    }

    "response filter chain (annotation fields)" in {
      val request = FacadeRequest(Uri("/users/{userId}", Map("userId" → "100500")), "get", Map.empty, Null)
      val context = mockContext(request.copy(uri=Uri(request.uri.formatted))).prepare(request)
      val response = FacadeResponse(200, Map.empty, ObjV("statusCode" → 100500, "processedBy" → "John"))
      val filters = filterChain.findResponseFilters(context, response)
      filters.head shouldBe a[ResponsePrivateFilter]
      filters.tail.head shouldBe a[NoOpFilter]
      filters.length should equal(2)
    }

    "event filter chain (annotation fields)" in {
      val request = FacadeRequest(Uri("/users/{userId}", Map("userId" → "100500")), "get", Map.empty, Null)
      val context = mockContext(request.copy(uri=Uri(request.uri.formatted))).prepare(request)
      val event = FacadeRequest(request.uri, "feed:put", Map.empty,
        ObjV("fullName" → "John Smith", "userName" → "jsmith", "password" → "neverforget")
      )
      val filters = filterChain.findEventFilters(context, event)
      filters.head shouldBe a[EventPrivateFilter]
      filters.length should equal(1)
    }
  }
}