package com.hypertino.facade.workers

import scaldi.Injector

import scala.concurrent.Await

class TestWsRestServiceApp(implicit inj: Injector) extends WsRestServiceApp {

  override def stopService(controlBreak: Boolean): Unit = {
    super.stopService(controlBreak)
    try {
      Await.result(hyperBus.shutdown(shutdownTimeout*4/5).runAsync, shutdownTimeout)
    } catch {
      case t: Throwable ⇒
        log.error("Hyperbus didn't shutdown gracefully", t)
    }
    try {
      Await.result(actorSystem.terminate(), shutdownTimeout)
    } catch {
      case t: Throwable ⇒
        log.error("ActorSystem wasn't terminated gracefully", t)
    }
  }
}
