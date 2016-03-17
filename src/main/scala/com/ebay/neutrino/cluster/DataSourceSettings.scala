package com.ebay.neutrino.cluster

import akka.actor._
import com.ebay.neutrino.config.Configuration
import com.ebay.neutrino.config.Configuration._
import com.typesafe.config.Config
import com.ebay.neutrino.datasource.DataSource

import scala.concurrent.duration.{Duration, FiniteDuration}


/**
 * Extension providing all settings available to the application:
 * - Monitor
 *
 */
case class DataSourceSettings(
  refreshPeriod:  FiniteDuration,
  datasourceReader : Class[DataSource]
)
  extends Extension
{
  def isEnabled() = (refreshPeriod != Duration.Zero)
}


object DataSourceSettings {


  // Initialization Constructor
  def apply(c: Config): DataSourceSettings =
    DataSourceSettings(
      c getOptionalDuration "refresh-period" getOrElse Duration.Zero,
      c getClass "datasource-reader"
    )

  def getDataSourceObject(): Unit = {

  }

}
