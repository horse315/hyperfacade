package com.hypertino.facade.workers

import akka.actor.ActorRef
import com.hypertino.facade.MockContext
import com.hypertino.facade.filter.chain.FilterChain
import com.hypertino.facade.model.{ContextWithRequest, FacadeRequest}
import spray.can.websocket.frame.TextFrame
import spray.can.{Http, websocket}
import spray.routing.HttpServiceActor

abstract class WsTestWorker(filterChain: FilterChain) extends HttpServiceActor with websocket.WebSocketServerWorker with MockContext {
  import context._
  private var _serverConnection: ActorRef = _

  def serverConnection = _serverConnection

  override def receive = {
    case Http.Connected(remoteAddress, localAddress) =>
      _serverConnection = sender()
      context.become(handshaking orElse closeLogic)
      serverConnection ! Http.Register(context.self)
  }

  def businessLogic: Receive = {
    case frame: TextFrame =>
      val facadeRequest = FacadeRequest(frame)
      val context = mockContext(facadeRequest)
      filterChain.filterRequest(ContextWithRequest(context, facadeRequest)) map { filteredCWR ⇒
        exposeFacadeRequest(filteredCWR.request)
      }
  }

  def exposeFacadeRequest(facadeRequest: FacadeRequest): Unit
}