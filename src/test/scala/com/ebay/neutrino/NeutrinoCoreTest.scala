package com.ebay.neutrino.config

import com.ebay.neutrino.NeutrinoCore
import org.scalatest.{Matchers, FlatSpec}

import scala.concurrent.Await
import scala.concurrent.duration._


class NeutrinoCoreTest extends FlatSpec with Matchers {

  // Hard-code a configuration
  val settings = NeutrinoSettings(Configuration.load("proxy.conf"))


  it should "startup and shutdown properly" in {
    val core = new NeutrinoCore(settings)

    // Start running. This will run until the process is interrupted...
    val start = core.start()

    // Try over a few seconds
    Await.ready(start, 500 millis).isCompleted should be (true)
    core.supervisor.isShuttingDown should be (false) // This includes shutdown and terminated

    // Now, trigger shutdown (ensure it's fast enough to not kill our tests)
    val stop = core.shutdown()
    val time = System.currentTimeMillis
    Await.ready(stop, 5 seconds)

    /*
    import core.context
    stop onComplete { _ =>
      println(s"Elapsed time = ${System.currentTimeMillis()-time millis}")
    }
    */

    // Check both futures for completion
    assert(start.isCompleted)
    assert(stop.isCompleted)
    assert(core.supervisor.isTerminated)
  }
}