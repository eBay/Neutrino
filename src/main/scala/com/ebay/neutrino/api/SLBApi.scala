package com.ebay.neutrino.api

import akka.actor.{Actor, ActorSystem, Props}
import com.ebay.neutrino.metrics.Instrumented
import com.ebay.neutrino.www.WebService
import com.ebay.neutrino.www.ui.ResourceServices
import com.typesafe.scalalogging.slf4j.StrictLogging
import spray.routing.SimpleRoutingApp
import com.ebay.neutrino.cluster.SystemConfiguration
import scala.util.{Failure, Success}


class SLBApi
  extends Actor
  with SimpleRoutingApp with ResourceServices with WebService
  with StrictLogging
  with Instrumented
{

  implicit override val system = context.system

  import context.dispatcher
  val config = SystemConfiguration(system)

  // Our web-application routes
  def routes =  resourceRoutes ~ webRoutes

  if (config.settings.enableApi) {
    // TODO pull these from configuration
    val host = "0.0.0.0"
    val port = 8079

    logger.info("Starting API on port ")

    //val simpleCache = routeCache(maxCapacity = 1000, timeToIdle = Duration("30 min"))
    startServer(interface = host, port = port)(routes) onComplete {
      case Success(b) =>
        println(s"Successfully bound to ${b.localAddress}")
      case Failure(ex) =>
        println(ex.getMessage)
    }
  }


  def receive: Receive = {

    case msg =>
      logger.warn("Unexpected message received: {}", msg.toString)
  }


}


/**
 * Standalone app wrapper around the SLB API.
 */
object SLBApi extends App {

  ActorSystem("slb-api").actorOf(Props(classOf[SLBApi]))

}