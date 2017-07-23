package com.hypertino.facade

import com.hypertino.binders.value.{Lst, Null, Obj, Text}
import com.hypertino.facade.apiref.auth.{Validation, ValidationResult}
import com.hypertino.facade.apiref.user.UsersGet
import com.hypertino.facade.filter.http.AuthenticationRequestFilter
import com.hypertino.facade.filter.parser.DefaultPredicateEvaluator
import com.hypertino.facade.model.RequestContext
import com.hypertino.hyperbus.model.annotations.request
import com.hypertino.hyperbus.model.{Created, DefinedResponse, DynamicBody, DynamicRequest, EmptyBody, ErrorBody, Forbidden, HRL, HeadersMap, MessagingContext, Method, Ok, Request, ResponseBase}
import monix.eval.Task

@request(Method.POST, "hb://auth-test/validations")
case class TestValidationsPost(
                            body: Validation
                          ) extends Request[Validation]
  with DefinedResponse[
    Created[ValidationResult]
    ]

object TestValidationsPost extends com.hypertino.hyperbus.model.RequestMetaCompanion[TestValidationsPost]{
  implicit val meta = this
  type ResponseType = Created[ValidationResult]
}

class AuthenticationRequestFilterSpec extends TestBase("inproc-test.conf") {
  val handlers = hyperbus.subscribe(this)
  Thread.sleep(500)

  def onTestValidationsPost(implicit post: TestValidationsPost): Task[ResponseBase] =
    if (post.body.authorization == "Test ABC") {
      Task.eval(Created(ValidationResult(
        identityKeys = Obj.from("user_id" → "100500", "email" → "info@example.com"),
        extra=Null
      )))
    } else {
      Task.raiseError(Forbidden(ErrorBody("forbidden")))
    }

  def onUsersGet(implicit get: UsersGet) = Task.eval[ResponseBase] {
    val email = get.headers.hrl.query.email
    if (email == Text("info@example.com")) {
      Ok(DynamicBody(Lst.from(Obj.from(
        "user_id" → "100500"
      ))))
    } else {
      Ok(DynamicBody(Lst.empty))
    }
  }

  "AuthenticationRequestFilter" - {
    "Set user in context" in {
      val filter = new AuthenticationRequestFilter(hyperbus, DefaultPredicateEvaluator,scheduler)
      implicit val mcx = MessagingContext.Implicits.emptyContext
      val rc = RequestContext(
        DynamicRequest(HRL("hb://test"), Method.GET, EmptyBody, HeadersMap(
          "Authorization" → "Test ABC"
        ))
      )
      val result = filter.apply(rc).futureValue
      result.contextStorage.user shouldBe Obj.from("user_id" → "100500")
      result.request.headers.get("Authorization-Result") shouldBe Some(Obj.from("user_id" → "100500"))
    }
  }
}
