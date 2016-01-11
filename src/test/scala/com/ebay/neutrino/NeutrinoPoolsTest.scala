package com.ebay.neutrino

import com.ebay.neutrino.config.{NeutrinoSettings, VirtualPool}
import org.scalatest.{FlatSpec, Matchers}


class NeutrinoPoolsTest extends FlatSpec with Matchers with NeutrinoTestSupport {

  implicit val core = new NeutrinoCore(NeutrinoSettings.Empty)

  def startCore() = false


  it should "ensure apply() maps to underlying state" in {
    // TODO

  }


  it should "rudmintary test of neutrino-pools wrapper" in {
    val pools = neutrinoPools()
    pools() shouldBe empty

    // Add a single pool
    pools.update(VirtualPool())
    pools().size should be (1)

    // Add two pools
    pools.update(VirtualPool(id="1"), VirtualPool(id="2"))
    pools().size should be (2)
    pools() map (_.settings.id.toInt) should be (Seq(1,2))

    // Add two pools
    pools.update(VirtualPool(id="3"))
    pools().size should be (1)
    pools() map (_.settings.id.toInt) should be (Seq(3))

    // Remove all pools
    pools.update()
    pools().size should be (0)
    pools() map (_.settings.id.toInt) should be (Seq())
  }


  it should "test massive concurrency access for safety" in {
    // TODO ...
  }

/*
  it should "resolve pool by name" in {
    val pools = new NeutrinoPools(null)
    val seq = Seq(VirtualPool("abc"), VirtualPool("123"), VirtualPool("zyx", protocol=Transport.HTTPS), VirtualPool("987"))

    // Setup the pools
    pools.update(seq:_*)
    pools().size should be (4)

    // Attempt to resolve by name
    pools.getNamed("abc").map(_.settings) should be (Some(VirtualPool("abc")))
    pools.getNamed("987").map(_.settings) should be (Some(VirtualPool("987")))
    pools.getNamed("ab7").map(_.settings) should be (None)

    // Should only match HTTP on default
    pools.getNamed("123", Transport.HTTP).map(_.settings) should be (Some(VirtualPool("123")))
    pools.getNamed("123", Transport.HTTPS).map(_.settings) should be (None)
    pools.getNamed("123").map(_.settings) should be (Some(VirtualPool("123", Transport.HTTP)))
    pools.getNamed("123").map(_.settings) should not be (Some(VirtualPool("123", Transport.HTTPS)))

    // Should only match HTTPS against specified
    pools.getNamed("zyx").map(_.settings) should be (None)
    pools.getNamed("zyx", Transport.HTTP).map(_.settings) should be (None)
    pools.getNamed("zyx", Transport.HTTPS).map(_.settings) should be (Some(VirtualPool("zyx", protocol=Transport.HTTPS)))
  }
  */
}