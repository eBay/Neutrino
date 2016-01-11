package com.ebay.neutrino.config

import java.net.InetSocketAddress

import com.ebay.neutrino.PoolResolver
import com.ebay.neutrino.config.Configuration._
import com.typesafe.config.Config
import io.netty.channel.ChannelHandler

import scala.concurrent.duration.Duration

/**
 * An individual listener/port/transport tuple.
 * Note that listener-interfaces are transport specific; we duplicate for ease of resolution.
 */
case class ListenerAddress(host: String, port: Int, protocol: Transport) extends VirtualAddress
{
  // Expose VirtualAddress as socket-address (?? Does this cache ??)
  lazy val socketAddress = new InetSocketAddress(host, port)
}


/**
 * Representation of a VIP/Interface Address/Listener.
 *
 * Note that LBaaS models LoadBalancer with the address and Listener (VIP) as port/protocol
 * only LBMS models VIP as containing both address/port.
 *
 * Validation:
 * - Host: Needs to be an IP or DNS address, but is expensive.
 *    @see http://stackoverflow.com/questions/106179/regular-expression-to-match-dns-hostname-or-ip-address
 * - Port: 0..65535
 *
 * @param addresses
 * @param protocol
 * @param handlers
 * @param poolResolver
 * @param timeouts
 */
case class ListenerSettings(
  addresses:      Seq[ListenerAddress],
  protocol:       Transport,
  sourcePorts:    Seq[Int],
  handlers:       Seq[_ <: ChannelHandler],
  poolResolvers:  Seq[_ <: PoolResolver],
  channel:        ChannelSettings,
  timeouts:       TimeoutSettings
)

object ListenerSettings {
  import com.ebay.neutrino.config.Configuration._


  // ListenerSettings factory type; this is the preferred construction
  def apply(cfg: Config) = {
    // Build a set of addresses around
    val hosts = cfg getStringOrList "host"
    val ports = cfg getIntOrList "port"
    val alias = cfg getIntOrList "port-alias"
    val proto = cfg getProtocol "protocol"

    // Get the cross-product of host:port
    val addresses = for { host <- hosts; port <- ports } yield ListenerAddress(host, port, proto)

    new ListenerSettings(
      addresses,
      proto,
      ports ++ alias,
      cfg getClassInstances "pipeline-class",
      cfg getStringOrList "pool-resolver" map (PoolResolver(_)),
      cfg getOptionalConfig "channel-options" map (ChannelSettings(_)) getOrElse ChannelSettings.Default,
      cfg getTimeouts "timeout"
    ) with
      HasConfiguration { override val config: Config = cfg }
  }


  // Factory type; create with some defaults.
  def apply(
    addresses:      Seq[ListenerAddress]=Seq(),
    protocol:       Transport=Transport.HTTP,
    sourcePorts:    Seq[Int]=Seq(),
    handlers:       Seq[_ <: ChannelHandler]=Seq(),
    poolResolvers:  Seq[_ <: PoolResolver]=Seq()): ListenerSettings =
  {
    ListenerSettings(addresses, protocol, sourcePorts, handlers, poolResolvers, ChannelSettings.Default, TimeoutSettings.Default)
  }

}


/**
 * Representation of downstream channel settings.
 *
 */
case class ChannelSettings(
  forceKeepAlive:   Boolean,
  auditThreshold:   Option[Duration]
)

object ChannelSettings {

  val Default = ChannelSettings(true, None)


  def apply(cfg: Config) =
    new ChannelSettings(
      cfg getBoolean "force-keepalive",
      cfg getOptionalDuration "audit-threshold"
    )
}