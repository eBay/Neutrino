package com.ebay.neutrino.config

import java.net.URI

import com.ebay.neutrino.NoResolver
import org.scalatest.{FlatSpec, Matchers}


class ConfigurationUtilsSpec extends FlatSpec with Matchers {
  behavior of "Configuration file parsing utils"

  val SimpleConfig = Configuration.load("proxy-duplicate.conf")


  it should "handle empty input" in {
    //assert(Set.empty.size === 0)
  }

  it should "parse simple VIP configuration" in {

    val settings = NeutrinoSettings(SimpleConfig)
    assert(settings.interfaces.size == 2)
    //assert(balancercfg.host == "balancer")
    //assert(balancercfg.defaultPool == "default")

    // Check the VIPs against expected
    {
      val vip = settings.interfaces(0)
      vip.addresses should be (Seq(ListenerAddress("0.0.0.0", 8080, Transport.HTTP)))
      vip.protocol should be (Transport.HTTP)
      vip.poolResolvers should be (Seq(NoResolver))    // TODO
      vip.handlers shouldNot be (empty)
    }
    {
      val vip = settings.interfaces(1)
      vip.addresses should be (Seq(ListenerAddress("0.0.0.0", 8088, Transport.HTTP)))
      vip.protocol should be (Transport.HTTP)
      vip.poolResolvers should be (Seq(NoResolver))
      vip.handlers should be (empty)
    }
  }


  it should "parse simple Pool configuration" in {

    val balancercfg = LoadBalancer(SimpleConfig)
    assert(balancercfg.pools.size == 2)

    {
      val pool = balancercfg.pools(0)
      assert(pool.servers.size == 0)
      assert(pool.health == None)
    }

    {
      // Check the VIP against expected
      val pool = balancercfg.pools(1)
      assert(pool.servers.size == 2)
      assert(pool.protocol == Transport.HTTP)

      assert(pool.health != None)
      assert(pool.health.get.monitorType == "type")
      assert(pool.health.get.monitor == None)
      assert(pool.health.get.path == new URI("/"))

      {
        val server = pool.servers(0)
        assert(server.host == "localhost")
        assert(server.port == 8081)
      }
      {
        val server = pool.servers(1)
        assert(server.host == "127.0.0.1")
        assert(server.port == 8082)
      }
    }
  }


  // TODO - VIP w. no default pool

  // TODO - bad URI
}