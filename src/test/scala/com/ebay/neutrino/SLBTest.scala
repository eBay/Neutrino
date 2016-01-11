package com.ebay.neutrino

/**
  * Created by blpaul on 12/7/15.
  */

import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._


class SLBTest extends FlatSpec with Matchers {


  it should "slb startup and shutdown properly" in {
    val slb = SLB.apply("multiport.conf")
    val start = slb.start()

    // Try over a few seconds
    Await.ready(start, 6000 millis).isCompleted should be (true)

    // Now, trigger shutdown (ensure it's fast enough to not kill our tests)
    val stop = slb.shutdown()
    val time = System.currentTimeMillis
    Await.ready(stop, 50 seconds)


    // Check both futures for completion
    assert(start.isCompleted)
    assert(stop.isCompleted)

  }
}
