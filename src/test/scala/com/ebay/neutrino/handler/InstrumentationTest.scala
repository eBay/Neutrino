package com.ebay.neutrino.handler

import com.codahale.metrics.MetricRegistry
import nl.grons.metrics.scala.Timer
import org.scalatest.FlatSpec


// Can be implemented as base-class of all three versions
class InstrumentationSpec extends FlatSpec {

  behavior of "Instrumentation"

  it should "handle simple host header" in {
  }
}

/**
 * Performance testing wrapper around header types.
 */
object InstrumentationPerf extends App {
  import scala.concurrent.duration._

  class Counter {
    var counter = 0.0
    def increment() = { counter = counter+1.1 }

    // Do some warmup
    for (i <- 0 until 10*Limit) { counter = counter + 1 }
    counter = 0.0
  }

  val Limit = 10*1000*1000   //10m
  val counter = new Counter

  val flags = Set(0, 4, 7)

  def time(count: Int = Limit)(f: => Unit): FiniteDuration = {
    val start = System.nanoTime
    var i = 0
    while (i < count) { f; i = i+1 }
    (System.nanoTime-start) nanos
  }

  val registry = new MetricRegistry()
  val timer  = new Timer(registry.timer("timer"))

  /**
   * From execution:
   *
   * Connected to the target VM, address: '127.0.0.1:61850', transport: 'socket'
   * Lambda wrapper elapsed: 15043000 nanoseconds, 1 nanosecond/op
   * Disconnected from the target VM, address: '127.0.0.1:61850', transport: 'socket'
   * Direct elapsed: 12222000 nanoseconds, 1 nanosecond/op
   *
   * Calls are too simple to measure properly; processor/JVM are just caching in registers
   * and inlining
   */
  {
    // test 10M calls to method directly
    val start = System.nanoTime
    var i = 0
    while (i < Limit) { counter.increment(); i = i+1 }
    val elapsed = (System.nanoTime-start) nanos;
    println(s"Direct elapsed: $elapsed, ${elapsed/Limit}/op")
  }

  {
    // test 10M calls to counter directly
    val metric = new MetricRegistry().counter("counter")
    val start = System.nanoTime
    var i = 0
    while (i < Limit) { metric.inc(); i = i+1 }
    val elapsed = (System.nanoTime-start) nanos;
    println(s"Direct counter elapsed: $elapsed, ${elapsed/Limit}/op")
  }

  {
    // Test 10M calls to method via a function wrapper
    val elapsed = time(Limit) { counter.increment() }
    println(s"Lambda wrapper elapsed: $elapsed, ${elapsed/Limit}/op")
  }

  {
    // Test 10M calls to method via a partial-function wrapper
    val elapsed = time(Limit) { counter.increment() }
    println(s"Lambda wrapper elapsed: $elapsed, ${elapsed/Limit}/op")
  }


  {
    // test 10M calls to metrics counter within lamda
    val metric = new MetricRegistry().counter("counter")
    val elapsed = time(Limit) { metric.inc(); counter.increment() }
    println(s"Lambda counter elapsed: $elapsed, ${elapsed/Limit}/op")
  }

  {
    // test 10M calls to metrics timer
    val elapsed = time(Limit) { timer.time(counter.increment()) }
    println(s"Lambda timer elapsed: $elapsed, ${elapsed/Limit}/op")
  }

  {
    // test 10M calls to metrics historgram with manual timing
    val histo = new MetricRegistry().histogram("histo")
    val elapsed = time(Limit) {
      val start = System.nanoTime
      counter.increment()
      histo.update(System.nanoTime-start)
    }
    println(s"Lambda histogram w. manual time elapsed: $elapsed, ${elapsed/Limit}/op")
  }


  /**
   * These seem to indicate that there is a memory threshold in the metrics collection
   * past which operations are computationally more expensive (which would make sense
   * re: the underlying data structure)
   *
   * If > 0.1% of the histogram size is
   */
  {
    // test 10M calls to method using a timing lookup for 30% histo
    val histo = new MetricRegistry().histogram("histo")
    val elapsed = time(Limit) {
      val start = System.nanoTime
      counter.increment()
      if (flags.contains((start % 100).toInt)) histo.update(System.nanoTime-start)
    }
    println(s"Lambda flag selection for 3% histogram w. manual time elapsed: $elapsed, ${elapsed/Limit}/op")
  }
  {
    // test 10M calls to method using a random for 30% histo
    val histo = new MetricRegistry().histogram("histo")
    val elapsed = time(Limit) {
      val start = System.nanoTime
      counter.increment()
      if ((start % 100).toInt < 3) histo.update(System.nanoTime-start)
    }
    println(s"Lambda 3% histogram w. manual time elapsed: $elapsed, ${elapsed/Limit}/op")
  }

  {
    // test 10-M calls to method using a timing lookup for 30% histo
    val histo = new MetricRegistry().histogram("histo")
    val elapsed = time(Limit) {
      val start = System.nanoTime
      counter.increment()
      if (flags.contains((start % 10000).toInt)) histo.update(System.nanoTime-start)
    }
    println(s"Lambda flag selection for << 1% histogram w. manual time elapsed: $elapsed, ${elapsed/Limit}/op")
  }
  {
    // test 10M calls to method using a random for 30% histo
    val histo = new MetricRegistry().histogram("histo")
    val elapsed = time(Limit) {
      val start = System.nanoTime
      counter.increment()
      if ((start % 10000).toInt < 3) histo.update(System.nanoTime-start)
    }
    println(s"Lambda << 1% histogram w. manual time elapsed: $elapsed, ${elapsed/Limit}/op")
  }
}