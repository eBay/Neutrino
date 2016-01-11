package com.ebay.neutrino.config

import com.ebay.neutrino.config.Configuration._
import com.typesafe.config.Config

import scala.collection.mutable


/**
 * Representation of a Pool
 */
case class VirtualPool(
  id:         String,
  port:       Int,
  protocol:   Transport,
  servers:    Seq[VirtualServer],
  addresses:  Seq[VirtualAddress],
  health:     Option[HealthSettings],
  balancer:   BalancerSettings,
  timeouts:   TimeoutSettings
)
{
  require(servers.map(_.id).distinct.size == servers.size, "Server IDs must be unique")
}


object VirtualPool {
  import scala.collection.JavaConversions._


  // Pool configuration factory
  def apply(servers: VirtualServer*): VirtualPool =
    VirtualPool("default", Transport.HTTP, servers)

  // Pool/Protocol configuration factory
  def apply(protocol: Transport, servers: VirtualServer*): VirtualPool =
    VirtualPool("default", protocol, servers)

  // Defaulted values configuration factor
  def apply(id: String="default", protocol: Transport=Transport.HTTP, servers: Seq[VirtualServer]=Seq(), address: Seq[CanonicalAddress]=Seq(), port: Int=80): VirtualPool =
    new VirtualPool(id, port, protocol, servers, address, None, BalancerSettings.Default, TimeoutSettings.Default)

  // Create with parent configuration deafults; this is the preferred way
  def apply(cfg: Config): VirtualPool = {

    var addresses: mutable.Buffer[CanonicalAddress] = mutable.Buffer()
    // Default the port to 80 if it is not specified
    val port = if (cfg hasPath "port") cfg getInt "port" else 80
    // Default the protocol to http if it is not specified
    val protocol = if (cfg hasPath "protocol") cfg getProtocol "protocol" else Transport.HTTP
    if (cfg hasPath "addresses") {
      addresses = cfg getConfigList "addresses" map (CanonicalAddress(_, port, protocol))
    }


    var wildcardAddresses: mutable.Buffer[WildcardAddress] = mutable.Buffer()
    if (cfg hasPath "wildcard") {
      wildcardAddresses = cfg getConfigList "wildcard" map (WildcardAddress(_, port))
    }


    new VirtualPool(
      cfg getString "id",
      port,
      protocol,
      cfg getConfigList "servers" map (VirtualServer(_)),
      addresses ++ wildcardAddresses,
      cfg getOptionalConfig "health" map (HealthSettings(_)),
      cfg getOptionalString "balancer" map (BalancerSettings(_)) getOrElse BalancerSettings.Default,
      cfg getTimeouts "timeout"
    ) with
      HasConfiguration {
      override val config: Config = cfg
    }
  }
}