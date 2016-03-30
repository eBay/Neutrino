package com.ebay.neutrino.cluster

import akka.actor.Actor
import com.ebay.neutrino.config.{LoadBalancer, Configuration}
import com.typesafe.scalalogging.slf4j.StrictLogging

import scala.concurrent.duration._
import scala.util.{Failure, Success}
import com.ebay.neutrino.datasource.DataSource


class SLBLoader extends Actor with StrictLogging {
  import context.dispatcher

  // Create a new SLB Configuration based off the file
  // Note that the system configuration is pulled from common.conf
  val config  = SystemConfiguration(context.system)
  val dataSourceReader  = config.settings.dataSource.datasourceReader.getConstructor().newInstance()



  // Schedule a configuration reload
  override def preStart() {
    context.system.scheduler.schedule(5 seconds, config.settings.dataSource.refreshPeriod, self, "reload")
  }


  def receive: Receive = {
    case "reload" =>
      // Create a new SLB configuration
      val results = dataSourceReader.load();
      logger.info("Reloading the configuration: {}")
      config.topology.update(results)
      sender ! "complete"

    case "complete" =>
      logger.info("Reloading of configuration complete")

    case msg =>
      logger.warn("Unexpected message received: {}", msg.toString)
  }
}