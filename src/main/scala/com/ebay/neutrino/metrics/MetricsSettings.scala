package com.ebay.neutrino.metrics

import java.util

import com.ebay.neutrino.config.HasConfiguration
import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration


sealed trait MetricsSettings
case class ConsoleMetrics(publishPeriod: FiniteDuration) extends MetricsSettings
case class GraphiteMetrics(publishPeriod: FiniteDuration, host: String, port: Int) extends MetricsSettings
case class UnknownMetrics(config: Config) extends MetricsSettings with HasConfiguration


object MetricsSettings {
  import com.ebay.neutrino.config.Configuration._

  import scala.collection.JavaConversions._


  // Extract a list of appropriate metric-settings
  def apply(list: util.List[_ <: Config]): List[MetricsSettings] =
    list.toList map (MetricsSettings(_))

  def apply(list: List[_ <: Config]): List[MetricsSettings] =
    list.toList map (MetricsSettings(_))

  // Extract the appropriate metric-settings based on the type specified in the config
  def apply(c: Config): MetricsSettings =
    c getString "type" toLowerCase match {
      case "console"  => console(c)
      case "graphite" => graphite(c)
      case _          => unknown(c)
    }

  // Extract settings for publishing console metrics
  def console(c: Config): MetricsSettings = ConsoleMetrics(
    c getDuration "publish-period"
  )

  // Extract settings for publishing graphite metrics
  def graphite(c: Config): MetricsSettings = GraphiteMetrics(
    c getDuration "publish-period",
    c getString "host",
    c getOptionalInt "port" getOrElse 2003
  )

  // Extract settings for unknown metrics
  def unknown(c: Config): MetricsSettings = UnknownMetrics(c)

}
