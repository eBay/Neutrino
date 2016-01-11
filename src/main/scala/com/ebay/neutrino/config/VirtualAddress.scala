package com.ebay.neutrino.config

import java.net.{InetSocketAddress, SocketAddress}

import com.typesafe.config.Config


/**
 * Representation of a VIP/Interface Address/Listener.
 *
 */
trait VirtualAddress {

  def socketAddress: SocketAddress
}

object VirtualAddress {

  // Borrowed from http://stackoverflow.com/questions/106179/regular-expression-to-match-dns-hostname-or-ip-address
  val ValidIpAddressRegex = """^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$""".r
  val ValidHostnameRegex  = """^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\-]*[a-zA-Z0-9])\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\-]*[A-Za-z0-9])$""".r

  // Determine if the IP provided is valid
  @inline def isIPValid(ip: String) = ValidIpAddressRegex.pattern.matcher(ip).matches

  // Determine if the hostname provided is valid (note - will match IPs loosely too)
  @inline def isHostnameValid(hostname: String) = ValidHostnameRegex.pattern.matcher(hostname).matches
}


/**
 * Note that LBaaS models LoadBalancer with the address and Listener (VIP) as port/protocol
 * only LBMS models VIP as containing both address/port.
 *
 * Validation:
 * - Host: Needs to be an IP or DNS address, but is expensive.
 *    @see http://stackoverflow.com/questions/106179/regular-expression-to-match-dns-hostname-or-ip-address
 * - Port: 0..65535
 *
 * @param host
 * @param port
 * @param protocol
 * @param handlers
 * @param timeouts
 */
case class CanonicalAddress(host: String, port: Int, protocol: Transport, timeouts: TimeoutSettings)
  extends VirtualAddress
{
  require(host.nonEmpty, "Host is required and was not provided")
  require(VirtualAddress.isHostnameValid(host), s"Host provided '$host' is not valid")

  lazy val socketAddress: SocketAddress = new InetSocketAddress(host, port)
}


object CanonicalAddress {
  import com.ebay.neutrino.config.Configuration._


  // VIP parent-configuation factory; this is the preferred way
  def apply(cfg: Config, port: Int, protocol : Transport): CanonicalAddress =
    new CanonicalAddress(
      cfg getString "host",
      port,
      protocol,
      TimeoutSettings.Default
    ) with
      HasConfiguration { override val config: Config = cfg }

  // VIP configuration factory; factory initializer
  def apply(host: String="localhost", port: Int=8080, protocol: Transport=Transport.HTTP) =
    new CanonicalAddress(host, port, protocol, TimeoutSettings.Default)

}


/**
 * VIP Address representation that can be applied to URI-based filtering.
 *
 * @param host
 * @param path
 */
case class WildcardAddress(host: String, path: String, port : Int)
  extends VirtualAddress
{
  require(host.nonEmpty, "Host is required and was not provided")
  require(VirtualAddress.isHostnameValid(host), s"Host provided '$host' is not valid")

  lazy val socketAddress: SocketAddress = new InetSocketAddress(host, port)


}

object WildcardAddress {
  import com.ebay.neutrino.config.Configuration._


  def apply(cfg: Config, port: Int): WildcardAddress =
    new WildcardAddress(
      cfg getString "host",
      cfg getString "path",
      port
    ) with
      HasConfiguration { override val config: Config = cfg }
}