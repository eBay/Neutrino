package com.ebay.neutrino

import com.ebay.neutrino.config.{Configuration, LoadBalancer, NeutrinoSettings}
import org.scalatest._

import scala.concurrent.Await


trait ProxyIntegrationTest extends BeforeAndAfterAll { self: Suite =>
  import scala.concurrent.duration._

  // Create default balancer settings from file.
  // Override to customize/hard-code settings
  def settings: NeutrinoSettings = NeutrinoSettings(Configuration.load(configurationFile))
  def config: LoadBalancer = LoadBalancer(Configuration.load(configurationFile))


  // Start balancer, using the configuration in the standard echo.conf file
  def configurationFile: String = null

  var core: NeutrinoCore = null

  override def beforeAll() {
    // Start running. This will run until the process is interrupted...
    core = NeutrinoCore(settings, config)
    core.start()

    super.beforeAll()
  }

  override def afterAll() {
    // Now, trigger shutdown (ensure it's fast enough to not kill our tests)
    val stop = core.shutdown()
    Await.ready(stop, 5 seconds)

    // Check both futures for completion
    // assert(start.isCompleted)
    assert(stop.isCompleted)
    assert(core.supervisor.isTerminated)

    super.afterAll()
  }
}