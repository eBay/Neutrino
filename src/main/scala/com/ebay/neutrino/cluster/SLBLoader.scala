package com.ebay.neutrino.cluster

import akka.actor.Actor
import com.ebay.neutrino.config.{LoadBalancer, Configuration}
import com.typesafe.scalalogging.slf4j.StrictLogging

import scala.concurrent.duration._
import scala.util.{Failure, Success}


class SLBLoader extends Actor with StrictLogging {
  import context.dispatcher

  // Create a new SLB Configuration based off the file
  // Note that the system configuration is pulled from common.conf
  val config  = SystemConfiguration(context.system)



  // Schedule a configuration reload
  override def preStart() {
    context.system.scheduler.schedule(5 seconds, config.settings.file.refreshPeriod, self, "reload")
  }


  def receive: Receive = {
    case "reload" =>
      // Create a new SLB configuration
      val results = Configuration.load("/etc/neutrino/slb.conf", "resolvers")
      logger.warn("Reloading the configuration: {}")
      config.topology.update(LoadBalancer(results))
      sender ! "complete"

    case "complete" =>
      logger.info("Reloading of configuration complete")

    case msg =>
      logger.warn("Unexpected message received: {}", msg.toString)
  }
}