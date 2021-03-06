package com.hypertino.facade.filter

import com.hypertino.binders.value.{Null, Text}
import com.hypertino.facade.TestBaseWithFacade
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, DynamicRequestObservableMeta, Method, Ok}
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import monix.execution.Ack.Continue

import scala.util.Success

class SetFilterSpec extends TestBaseWithFacade("inproc-test.conf", Seq("set-filter.raml")) {
  "SetFilter" should "set query" in {
    val t = testObjects
    import t._
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service", Method.GET, None))
      ).subscribe { implicit cmd =>
        cmd.reply(Success {
          Ok(DynamicBody(cmd.request.headers.hrl.query))
        })
        Continue
      }
    }

    httpGet("http://localhost:54321/set-query") shouldBe """{"filter":"abc"}"""
  }

  it should "set headers" in {
    val t = testObjects
    import t._
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service", Method.GET, None))
      ).subscribe { implicit cmd =>
        cmd.reply(Success {
          Ok(DynamicBody(cmd.request.headers.get("filter").getOrElse(Null)))
        })
        Continue
      }
    }

    httpGet("http://localhost:54321/set-headers") shouldBe """"abc""""
  }

  it should "set context" in {
    val t = testObjects
    import t._
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service", Method.GET, None))
      ).subscribe { implicit cmd =>
        cmd.reply(Success {
          Ok(DynamicBody(cmd.request.headers.hrl.query))
        })
        Continue
      }
    }

    httpGet("http://localhost:54321/set-context") shouldBe """{"x":"130"}"""
  }
}
