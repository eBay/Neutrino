package com.ebay.neutrino.config

import java.net.URI

import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._


/**
 * Parse unit-test for Neutrino-configuration
 */
class ConfigurationTest extends FlatSpec with Matchers {
  behavior of "Configuration file loading"

  // Desired prototype
  val healthSettings = Option(HealthSettings("type", URI.create("/"), None))

  // Cache the default timeouts from reference.conf
  val poolTimeouts = TimeoutSettings(Configuration.load() getConfig "pool.timeout")


  it should "parse test-env.conf without env" in {
    // Just the default
    val config = Configuration.load("test-env.conf")
    config shouldNot be(null)

    val settings = LoadBalancer(config)
    settings shouldNot be(null)
    settings.pools.size shouldBe 1
    settings.pools(0) should be (VirtualPool("default", 80, Transport.HTTP, Seq(), Seq(), healthSettings, BalancerSettings.Default, poolTimeouts))
    //settings.pools(0).addresses shouldBe empty
  }

  it should "parse test-env.conf with env overriding value" in {
    // Just the default
    val config = Configuration.load("test-env.conf", "test")
    config shouldNot be(null)

    val settings = LoadBalancer(config)
    settings shouldNot be(null)

    settings.pools.size shouldBe 1
    settings.pools(0) should be(VirtualPool("default", 80, Transport.HTTP, Seq(), Seq(), healthSettings, BalancerSettings.Default, poolTimeouts))
    //settings.pools(0).addresses.size shouldBe 1
  }

  it should "parse test-env.conf with env negating value" in {
    // Just the default
    val config = Configuration.load("test-env.conf", "qa")
    config shouldNot be(null)

    val settings = LoadBalancer(config)
    settings shouldNot be(null)
    settings.pools shouldBe empty
    //settings.vips shouldBe empty
  }
}


class ConfigurationVirtualAddressTest extends FlatSpec with Matchers {
  behavior of "Configuration file virtual-address (VIP) settings"
}

class ConfigurationPoolTest extends FlatSpec with Matchers {
  behavior of "Configuration file pool settings"
}


class ConfigurationTimeoutTest extends FlatSpec with Matchers {
  behavior of "Configuration file timeout settings"
  import com.ebay.neutrino.config.Configuration._


  it should "parse default settings (reference.conf)" in {
    val timeout = Configuration.load().getConfig("timeout")
    timeout shouldNot be (null)

    // Check path existance
    timeout.hasPath("session-timeout") should be (true)
    timeout.hasPath("request-timeout") should be (true)
    timeout.hasPath("read-idle-timeout") should be (true)
    timeout.hasPath("write-idle-timeout") should be (true)
    timeout.hasPath("write-timeout") should be (true)

    // Check for expected values
    timeout.getDuration("session-timeout") should be (2 minutes)
    timeout.getDuration("request-timeout") should be (30 seconds)
    timeout.getDuration("read-idle-timeout") should be (30 seconds)
    timeout.getDuration("write-idle-timeout") should be (30 seconds)
    timeout.getDuration("write-timeout") should be (5 seconds)
  }

  it should "parse default settings (reference.conf) of inheriting subtypes" in {
    val config= Configuration.load()

    // Check for expected values
    val timeout = config.getConfig("pool.timeout")
    timeout.getDuration("session-timeout") should be (2 minutes)
    timeout.getDuration("request-timeout") should be (30 seconds)
    timeout.getDuration("read-idle-timeout") should be (30 seconds)
    timeout.getDuration("write-idle-timeout") should be (30 seconds)
    timeout.getDuration("write-timeout") should be (5 seconds)
  }

  /** Currently can only promote from within the configuration resolver
    *
  it should "parse merged settings (proxy.conf) of inheriting defaults" in {
    val config = Configuration.load("proxy.conf")

    { // Check for overridden VIP values
    val timeout = config.getConfigList("vips").get(0).getConfig("timeout")
      timeout.getDuration("channel-timeout") should be (2 minutes)
      timeout.getDuration("request-timeout") should be (30 seconds)
      timeout.getDuration("idle-timeout") should be (2 seconds)
      timeout.getDuration("write-timeout") should be (0 seconds)
    }

    { // Check for overridden pool values
    val timeout = config.getConfigList("pools").get(0).getConfig("timeout")
      timeout.getDuration("channel-timeout") should be (2 minutes)
      timeout.getDuration("request-timeout") should be (30 seconds)
      timeout.getDuration("idle-timeout") should be (2 seconds)
      timeout.getDuration("write-timeout") should be (0 seconds)
    }
  }
    */


  it should "parse merged settings of inheriting overrides" in {
    val config = Configuration.load("test-timeout.conf", "test-one")

    { // Check for overridden default
      val timeout = config.getConfig("timeout")
      timeout.getDuration("session-timeout") should be (2 minutes)
      timeout.getDuration("request-timeout") should be (1 minute)   // OVERRIDE
      timeout.getDuration("read-idle-timeout") should be (30 seconds)
      timeout.getDuration("write-idle-timeout") should be (30 seconds)
      timeout.getDuration("write-timeout") should be (5 seconds)
    }

    /** Currently can only promote from within the configuration resolver
    { // Check for overridden VIP values
      val timeout = config.getConfig("vip.timeout")
      timeout.getDuration("channel-timeout") should be (2 minutes)
      timeout.getDuration("request-timeout") should be (1 minute) // overridden
      timeout.getDuration("idle-timeout") should be (2 seconds)
      timeout.getDuration("write-timeout") should be (0 seconds)
    }

    { // Check for overridden pool values
      val timeout = config.getConfig("pool.timeout")
      timeout.getDuration("channel-timeout") should be (2 minutes)
      timeout.getDuration("request-timeout") should be (1 minute) // overridden
      timeout.getDuration("idle-timeout") should be (2 seconds)
      timeout.getDuration("write-timeout") should be (0 seconds)
    }
    */
  }
}