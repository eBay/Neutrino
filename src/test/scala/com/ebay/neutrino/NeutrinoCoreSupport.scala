package com.ebay.neutrino

import com.ebay.neutrino.config._
import com.typesafe.config.Config
import org.scalatest.{BeforeAndAfterAll, Suite}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Test base-class for NeutrinoCore configurations.
 *
 */
trait NeutrinoCoreSupport extends BeforeAndAfterAll with NeutrinoTestSupport { self: Suite =>

  private var _settings: NeutrinoSettings = null
  private var _core: NeutrinoCore = null

  implicit def core: NeutrinoCore = _core
  implicit def settings: NeutrinoSettings = _settings
  implicit def config: Config = Configuration.load()

  // Default a configuration
  def createSettings()(implicit config: Config) = NeutrinoSettings(config)

  // By default, start the core on startup
  def startCore() = true

  // Update the core/service's topology
  def update(pools: VirtualPool*) = core.services.head.update(pools:_*)

  // Before test-classes, start NeutrinoCore
  override def beforeAll() = {
    super.beforeAll()

    _settings = createSettings()
    _core = new NeutrinoCore(_settings)
  }

  override def afterAll() = {
    _core = null
    _settings = null

    super.afterAll()
  }

}



trait NeutrinoCoreRunning extends NeutrinoCoreSupport { self: Suite =>

  // Before test-classes, start NeutrinoCore
  override def beforeAll() = {
    super.beforeAll()
    Await.result(core.start(), 5 seconds)
  }

  override def afterAll() = {
    Await.result(core.shutdown(), 5 seconds)
    super.afterAll()
  }
}