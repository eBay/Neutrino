package com.ebay.neutrino.config

import org.scalatest.{FlatSpec, Matchers}


class VirtualServerTest extends FlatSpec with Matchers {
  behavior of "VirtualServer parsing and construction"


  it should "parse simple VIP configuration from config" in {

    val config = LoadBalancer(Configuration.load("test_virtualserver.conf"))

    config.pools.size should be (1)

    {
      val pool = config.pools(0)
      pool.id should be ("default")
      pool.protocol should be (Transport.HTTP)
      pool.servers shouldBe empty
    }
  }
}