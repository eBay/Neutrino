package com.ebay.neutrino.cluster

import akka.actor._
import com.ebay.neutrino.config.Configuration
import com.ebay.neutrino.config.Configuration._
import com.typesafe.config.Config

import scala.concurrent.duration.{Duration, FiniteDuration}


/**
 * Extension providing all settings available to the application:
 * - Monitor
 *
 */
case class FileSettings(
  refreshPeriod:  FiniteDuration
)
  extends Extension
{
  def isEnabled() = (refreshPeriod != Duration.Zero)
}


object FileSettings extends ExtensionId[FileSettings] with ExtensionIdProvider {

  override def lookup() = FileSettings

  override def createExtension(system: ExtendedActorSystem) =
    FileSettings(system.settings.config getConfig "neutrino.file")


  // Create our settings object from the filename, if provided
  def apply(filename: String=null): FileSettings =
    apply(Configuration.load(filename) getConfig "file")


  // Initialization Constructor
  def apply(c: Config): FileSettings =
    FileSettings(
      c getOptionalDuration "file-refresh-period" getOrElse Duration.Zero
    )

}
