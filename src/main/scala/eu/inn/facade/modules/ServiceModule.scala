package eu.inn.facade.modules

import akka.actor.ActorSystem
import com.typesafe.config.Config
import eu.inn.facade.events.SubscriptionsManager
import eu.inn.facade.http.{StatusMonitorFacade, HandleErrorsDirectives}
import eu.inn.facade.{HyperBusFactory, StatsReporterFactory}
import eu.inn.hyperbus.HyperBus
import scaldi.Module

import scala.concurrent.ExecutionContext

class ServiceModule extends Module {
  bind [HyperBusFactory]        identifiedBy 'hbFactory            to new HyperBusFactory(inject [Config])
  bind [HyperBus]               identifiedBy 'hyperbus             to inject [HyperBusFactory].hyperBus
  bind [StatsReporterFactory]   identifiedBy 'statsFactory         to new StatsReporterFactory(inject [Config])
  bind [ActorSystem]            identifiedBy 'actorSystem          to ActorSystem("Inn", inject [Config])
  bind [ExecutionContext]       identifiedBy 'executionContext     to inject [ActorSystem].dispatcher
  bind [HandleErrorsDirectives] identifiedBy 'errorsDirectives     to new HandleErrorsDirectives
  bind [StatusMonitorFacade]    identifiedBy 'statusMonitorFacade  to new StatusMonitorFacade
  bind [SubscriptionsManager]   identifiedBy 'subscriptionsManager to new SubscriptionsManager
}
