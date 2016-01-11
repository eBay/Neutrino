package com.ebay.neutrino

/**
 * Created by blpaul on 11/13/2015.
 */

import com.ebay.neutrino.api.SLBApi
import com.ebay.neutrino.config.{Configuration, LoadBalancer}
import com.typesafe.scalalogging.slf4j.StrictLogging
import akka.actor.{ActorSystem, Props}
import com.ebay.neutrino.cluster.SLBLoader
import com.ebay.neutrino.cluster.SystemConfiguration

import scala.concurrent.Future

/**
 * Create an HTTP SLB application
 *
 *
 */
class SLB(system: ActorSystem) extends StrictLogging
{
  // Start a configuration-loader
  val config   = SystemConfiguration(system)
  val api      = system.actorOf(Props(classOf[SLBApi]), "api")
  val reloader = system.actorOf(Props(classOf[SLBLoader]), "loader")


  // Start running. This will run until the process is interrupted or stop is called
  def start(): Future[_] = {
    logger.warn("Starting SLB...")
    config.core.start()
  }

  def shutdown(): Future[_] = {
    logger.info(s"Stopping SLB Service")
    system.shutdown()
    config.core.shutdown()
  }
}


object SLB extends StrictLogging {

  /**
   * Initialize a new SLB instance, using the configuration file provided.
   *
   * @param filename
   * @return
   */
  def apply(filename: String = "/etc/neutrino/slb.conf"): SLB =
    new SLB(SystemConfiguration.system(filename))


  def main(args: Array[String]): Unit = {
    // If running as a stand-alone application, start
    //new SLBLifecycle().start(args)
    SLB().start()
  }
}


