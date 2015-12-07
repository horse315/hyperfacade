package eu.inn.facade.http

import eu.inn.binders.dynamic.Null
import eu.inn.binders.json._
import eu.inn.facade.HyperBusComponent
import eu.inn.hyperbus.IdGenerator
import eu.inn.hyperbus.model.DynamicBody
import eu.inn.hyperbus.model.standard.{DynamicGet, EmptyBody, ErrorResponse}
import eu.inn.util.akka.ActorSystemComponent
import eu.inn.util.http.RestServiceComponent
import eu.inn.util.{ConfigComponent, Logging}
import spray.http.ContentTypes._
import spray.http._
import spray.routing._

trait StatusMonitorFacade {
  this: ActorSystemComponent
    with RestServiceComponent
    with HyperBusComponent
    with ConfigComponent
    with Logging ⇒

  lazy val statusMonitorRoutes = new RestRoutes {

    lazy val routes: Route =
      get {
        complete {
          hyperBus <~ DynamicGet("/status-monitor", DynamicBody(EmptyBody.contentType, Null)) map { result ⇒
            HttpResponse(StatusCodes.OK, HttpEntity(`application/json`, result.body.content.toJson))
          } recover {
            case er: ErrorResponse ⇒
              HttpResponse(er.status, HttpEntity(`application/json`, er.body.toJson))

            case t: Throwable ⇒
              exceptionToHttpResponse(t)
          }
        }
      }
  }

  private def exceptionToHttpResponse(t: Throwable): HttpResponse = {
    val errorId = IdGenerator.create()
    log.error("Can't handle request. #" + errorId, t)
    HttpResponse(StatusCodes.InternalServerError, t.toString + " #"+errorId)
  }
}
