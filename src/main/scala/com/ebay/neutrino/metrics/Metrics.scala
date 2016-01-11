package com.ebay.neutrino.metrics

import com.ebay.neutrino.channel.NeutrinoSession
import com.ebay.neutrino.handler.NeutrinoRequestHandler
import com.ebay.neutrino.{NeutrinoCore, NeutrinoNode}
import io.netty.channel.Channel
import nl.grons.metrics.scala._


/**
 * Yammer metrics implementation based on metrics-scala (https://github.com/erikvanoosten/metrics-scala)
 * Static metrics (mapped directly to their key equivalent).
 *
 * @author hzariv on 8/29/2014.
 * @author cbrawn on 2/13/2015; static metrics direct link
 */
object Metrics extends Instrumented {
  import com.ebay.neutrino.config.{CompletionStatus => Status}
  import com.ebay.neutrino.metrics.{MetricsKey => Key}

  /** The application wide metrics registry. */
  val metricRegistry = new com.codahale.metrics.MetricRegistry()


  // Requests
  val RequestsOpen            = metrics.counter(Key.Requests, "open")
  val RequestsTotal           = metrics.meter  (Key.Requests, "total")
  val RequestsCompleted       = metrics.timer  (Key.Requests, "duration")

  // Store bucketed response detail as well;  should we have both total and each? or just each?
  val RequestsCompletedType   = Map[Status, Timer] (
    Status.Incomplete        -> metrics.timer(Key.RequestsCompleted, "none"),
    Status.Status2xx         -> metrics.timer(Key.RequestsCompleted, "2xx"),
    Status.Status4xx         -> metrics.timer(Key.RequestsCompleted, "4xx"),
    Status.Status5xx         -> metrics.timer(Key.RequestsCompleted, "5xx"),
    Status.Other             -> metrics.timer(Key.RequestsCompleted, "other"))

  // Sessions
  val SessionActive           = metrics.counter(Key.Session, "open")
  val SessionTotal            = metrics.meter  (Key.Session, "total")
  val SessionDuration         = metrics.timer  (Key.Session, "duration")

  // Channels: Upstream/Browser
  val UpstreamOpen            = metrics.counter(Key.UpstreamChannel, "open")
  val UpstreamTotal           = metrics.meter  (Key.UpstreamChannel, "total")
  val UpstreamCompleted       = metrics.timer  (Key.UpstreamChannel, "complete")
  val UpstreamBytesRead       = metrics.counter(Key.UpstreamChannel, "bytes.read")
  val UpstreamBytesWrite      = metrics.counter(Key.UpstreamChannel, "bytes.write")
  val UpstreamPacketsRead     = metrics.meter  (Key.UpstreamChannel, "packets.read")
  val UpstreamPacketsWrite    = metrics.meter  (Key.UpstreamChannel, "packets.write")

  // Channels: Downstream/Server
  val DownstreamOpen          = metrics.counter(Key.DownstreamChannel, "open")
  val DownstreamTotal         = metrics.meter  (Key.DownstreamChannel, "total")
  val DownstreamCompleted     = metrics.timer  (Key.DownstreamChannel, "complete")
  val DownstreamBytesRead     = metrics.counter(Key.DownstreamChannel, "bytes.read")
  val DownstreamBytesWrite    = metrics.counter(Key.DownstreamChannel, "bytes.write")
  val DownstreamPacketsRead   = metrics.meter  (Key.DownstreamChannel, "packets.read")
  val DownstreamPacketsWrite  = metrics.meter  (Key.DownstreamChannel, "packets.write")

  // Channels: Connection-pooling
  val PooledCreated           = metrics.meter  (Key.PoolAllocated, "created")
  val PooledRecreated         = metrics.meter  (Key.PoolAllocated, "recreated")
  val PooledAssigned          = metrics.meter  (Key.PoolAllocated, "assigned")
  val PooledRetained          = metrics.counter(Key.PoolReleased,  "retained")
  val PooledFailed            = metrics.counter(Key.PoolReleased,  "failed")
  val PooledExpired           = metrics.counter(Key.PoolReleased,  "expired")
  val PooledClosed            = metrics.counter(Key.PoolReleased,  "closed")
}


/**
 * Metric-key constants. These are the common metrics for core-system.
 *
 * Consumers shouldn't need to refer to these directly; they should be able to use
 * the static bindings in Metrics.
 *
 * @author cbrawn
 */
object MetricsKey {

  // Core
  val BossThreads             = MetricName(classOf[NeutrinoCore], "threads.boss")
  val IOThreads               = MetricName(classOf[NeutrinoCore], "threads.io")

  // Requests
  val Requests                = MetricName(classOf[NeutrinoRequestHandler], "requests")
  val RequestsCompleted       = Requests.append("completed")

  // Sesssions
  val Session                 = MetricName(classOf[NeutrinoSession])

  // Channels
  val Channel                 = MetricName(classOf[Channel])
  val UpstreamChannel         = Channel.append("upstream")
  val DownstreamChannel       = Channel.append("downstream")

  // Channels: Connection-pooling
  val PoolAllocated           = MetricName(classOf[NeutrinoNode], "allocate")
  val PoolReleased            = MetricName(classOf[NeutrinoNode], "release")
}


object HealthCheck {
  /** The application wide health check registry. */
  val healthChecksRegistry = new com.codahale.metrics.health.HealthCheckRegistry()
}

/**
 * This mixin trait can used for creating a class which creates health checks.
 */
trait Checked extends nl.grons.metrics.scala.CheckedBuilder {
  val healthCheckRegistry = HealthCheck.healthChecksRegistry
}
