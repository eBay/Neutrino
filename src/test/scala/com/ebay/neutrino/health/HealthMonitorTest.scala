package com.ebay.neutrino.health

import com.ebay.neutrino.NeutrinoCore
import com.ebay.neutrino.config._
import com.ebay.neutrino.metrics.HealthMonitor
import com.typesafe.config.Config


/**
 * To reuse an endpoint/service? Or just create/wrap our own...
 * @param settings
 */
class Status200Monitor(config: Config) extends HealthMonitor
{
  // Wrap in a simple Netty Connection
  def check(server: VirtualServer)(implicit balancer: NeutrinoCore) = {
    // Make dedicated connection
    /** TODO
    val address = server.socketAddress
    val client = new HttpEndpointClient(None)
     */
  }
}


object HealthMonitorSpec {

  case class TestHealthMonitorA() extends HealthMonitor {}
  case class TestHealthMonitorB(settings: HealthSettings) extends HealthMonitor {}
  case class TestHealthMonitorC(settings: String) extends HealthMonitor {}
}