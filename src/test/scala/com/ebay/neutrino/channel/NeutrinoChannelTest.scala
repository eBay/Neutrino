package com.ebay.neutrino.channel

import com.ebay.neutrino.NeutrinoCore
import com.ebay.neutrino.config.{Configuration, LoadBalancer, NeutrinoSettings}
import org.scalatest._

/**
 * Parse unit-test for Neutrino-configuration
 *
 * Note - needs echo-server running. TODO fix this...
 *
 * Notes:
 * - we currently use Apache HttpComponents async-client, which is basically our 3rd choice
 * - tried using HttpUnit but too textual, bad API, and no future/async support
 * - tried using Spray.io; very easy to use but no HTTP 1.0
 * - could have used Netty async but setup difficult and would like to use something else
 *    for compliancy reasons
 */
@Ignore
class NeutrinoChannelTest extends FlatSpec with Matchers {
  behavior of "Neutrino Channel initialization"

  // Create default balancer settings from file.
  // Override to customize/hard-code settings
  val cfgfile  = Configuration.load("proxy.conf")
  val settings = NeutrinoSettings(cfgfile)
  val config   = LoadBalancer(cfgfile)
  var balancer = NeutrinoCore(settings, config)


  it should "execute a byte-array" in {
    // TODO
    //val channel = new NeutrinoChannel(balancer)
    //channel.writeInbound()
  }


  // TODO test for correct number of channelRegistered/channelActive

}