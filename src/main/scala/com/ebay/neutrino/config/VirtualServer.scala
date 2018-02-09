package com.ebay.neutrino.config

import java.net.InetSocketAddress

import com.typesafe.config.Config


// Representation of a Server
case class VirtualServer(
  id: String,
  host: String,
  port: Int,
  weight: Option[Int] = None,
  health: Option[HealthSettings] = None
)
  extends VirtualAddress {
  // Expose VirtualAddress as socket-address (?? Does this cache ??)
  lazy val socketAddress = new InetSocketAddress(host, port)

  // Mutable health state
  @transient var healthState: HealthState = HealthState.Unknown
}


object VirtualServer {

  import Configuration._


  /**
    * VirtualServer configuration factory.
    */
  def apply(cfg: Config): VirtualServer =
    new VirtualServer(
      cfg getOptionalString "id" getOrElse (cfg getString "host"), // fallback to 'host'
      cfg getString "host",
      cfg getInt "port",
      if (cfg hasPath "weight") Option(cfg getInt "weight") else None
    ) with
      HasConfiguration {
      override val config: Config = cfg
    }
}


// Representation of a Backend Service
//case class Service()
