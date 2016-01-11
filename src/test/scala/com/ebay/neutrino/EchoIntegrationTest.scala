package com.ebay.neutrino

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import org.scalatest._


trait EchoIntegrationTest extends BeforeAndAfterAll { self: Suite =>

  private val echoFile = "echo.conf"
  private var echoSystem: ActorSystem = null


  override def beforeAll() {
    // Start echo server
    // the handler actor replies to incoming HttpRequests
    val config = ConfigFactory.load(echoFile)
    val name   = s"echo-server"

    echoSystem = ActorSystem("EchoIntegrationTest", config)
    echoSystem.actorOf(Props[EchoServer], "server")

    super.beforeAll()
  }

  override def afterAll() {
    // Shut the actor-system down
    echoSystem.shutdown()
    echoSystem = null

    super.afterAll()
  }
}