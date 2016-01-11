package com.ebay.neutrino.metrics

import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.TimeUnit

import com.codahale.metrics.ConsoleReporter
import com.codahale.metrics.graphite.{Graphite, GraphiteReporter}
import com.ebay.neutrino.config.Configuration
import com.ebay.neutrino.{NeutrinoCore, NeutrinoLifecycle}
import com.typesafe.config.Config
import com.typesafe.scalalogging.slf4j.StrictLogging


/**
 * Simple initializer for configuring console-support for metrics.
 *
 */
class MetricsLifecycle(settings: Seq[MetricsSettings]) extends NeutrinoLifecycle with StrictLogging {

  val registry = Metrics.metricRegistry

  // TODO resolve this properly
  val hostname = InetAddress.getLocalHost.getHostName

  // Our configured reporters
  val reporters =
    settings flatMap {
      case ConsoleMetrics(period) =>
        val reporter = ConsoleReporter.forRegistry(registry)
          .convertRatesTo(TimeUnit.SECONDS)
          .convertDurationsTo(TimeUnit.MILLISECONDS)
          .build()
        Option(reporter -> period)

      case GraphiteMetrics(period, host, port) =>
        val graphite = new Graphite(new InetSocketAddress(host, port))
        val reporter = GraphiteReporter.forRegistry(registry)
          .prefixedWith(hostname)
          .convertRatesTo(TimeUnit.SECONDS)
          .convertDurationsTo(TimeUnit.MILLISECONDS)
          //.filter(MetricFilter.ALL)
          .build(graphite)
        Option(reporter -> period)

      case _ => None
    }


  logger.info("Setting our hostname to {} for metrics reporting.", hostname)


  /**
   * Overloaded Constructor; create a MetricsLifecycle with a Config object.
   *
   * This will be used by the plugin reflection (for dynamic class creation) to inject
   * a runtime configuration into class creation.
   */
  def this(config: Config) = this(MetricsLifecycle.settings(config))


  // Start any available reporters
  def start() =
    reporters foreach {
      case (reporter, period) => reporter.start(period.toSeconds, TimeUnit.SECONDS)
    }

  // Stop all reporters (if appropriate)
  def shutdown() =
    reporters foreach {
      case (reporter, period) => reporter.stop()
    }


  // Neutrino Lifecycle support
  def start(balancer: NeutrinoCore) = start()
  def shutdown(balancer: NeutrinoCore) = shutdown()
}

object MetricsLifecycle {

  import Configuration._

  def settings(config: Config) =
    config getOptionalConfigList "metrics" map (MetricsSettings(_))

}