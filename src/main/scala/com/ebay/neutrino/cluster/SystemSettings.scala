package com.ebay.neutrino.cluster

import java.io.File

import akka.actor._
import com.ebay.neutrino.NeutrinoCore
import com.ebay.neutrino.config.Configuration._
import com.ebay.neutrino.config.{Configuration, NeutrinoSettings}
import com.typesafe.config.{ConfigFactory, Config}


case class SystemSettings(
  enableApi:    Boolean,
  neutrino:     NeutrinoSettings,
  dataSource:   DataSourceSettings
)

object SystemSettings {

  // This config is already at 'ebay.neutrino'
  def apply(config: Config): SystemSettings =
    SystemSettings(
      config getBoolean "enable-api",
      NeutrinoSettings(config),
      DataSourceSettings(config getConfig "datasource")
    )
}


case class SystemConfigurationExtension(system: ExtendedActorSystem) extends Extension
{
  // Extract 'ebay.neutrino' config
  val config = Configuration.load(system.settings.config, "resolvers")

  // Load system-settings (including all component settings)
  val settings = SystemSettings(config)

  // Initialize our Neutrino-core
  val core = new NeutrinoCore(settings.neutrino)

  // Our use-specific state cluster topology (customized for SLB)
  val topology = {
    new SLBTopology(core)
  }


}

/**
 * System-configuration extension for both Monitor and SLB.
 */
object SystemConfiguration extends ExtensionId[SystemConfigurationExtension] with ExtensionIdProvider {

  /** Cache our common configuration */
  private val common = ConfigFactory.load("slb.conf")

  override def lookup() = SystemConfiguration

  override def createExtension(system: ExtendedActorSystem) = SystemConfigurationExtension(system)

  def load(filename: String): Config =
    filename match {
      case null => common
      case file => val slbFile = new File(filename)
        val slbConfig = ConfigFactory.parseFile(slbFile)
        if (slbConfig.isEmpty) {
          common
        } else {
          ConfigFactory.load(slbConfig)
        }
    }

  def system(filename: String): ActorSystem =
    ActorSystem("slb-cluster", load(filename))

  // Create an actor-system and return the attached configuration, all in one
  def apply(filename: String): SystemConfigurationExtension =
    SystemConfiguration(system(filename))

}