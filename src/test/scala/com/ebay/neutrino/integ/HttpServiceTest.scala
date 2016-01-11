package com.ebay.neutrino.integ

import com.ebay.neutrino.NeutrinoCoreRunning
import com.ebay.neutrino.config.{TimeoutSettings, Configuration}
import com.typesafe.config.Config
import org.scalatest._
import spray.http.{HttpMethods, HttpRequest, StatusCodes, Uri}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Regression test for HttpRequests.
 */
class HttpServiceTest extends FlatSpec with NeutrinoCoreRunning with SprayRequestSupport with Matchers {
  behavior of "Http Service Test initialization"

  // Override the default configuration
  override implicit val config: Config = Configuration.load("http-service.conf")

  // Reduced connection-timeout for testing pool connections
  val poolTimeout = TimeoutSettings.Default.copy(connectionTimeout = 1 second)


  it should "get a 504 repsonse to an pool with bad downstream member-info" in {
    // Configure pool for a non-mapped server
    val badport = 1
    val server  = virtualServer("localhost", badport)
    val pool    = virtualPool("default", server).copy(timeouts = poolTimeout)

    // Update the topology
    core.services.head.update(pool)
    core.services.head.pools.toSeq should be (Seq(pool))

    // Send a simple request and wait briefly for a repsonse
    val get = HttpRequest(HttpMethods.GET, Uri("http://localhost:8080/"))
    val response = Await.result(send(get), 3 seconds)

    response.status should be (StatusCodes.GatewayTimeout)
    response.entity.asString should include ("Error 504 Gateway Timeout")
  }


  it should "get a 503 repsonse to an pool with no available members" in {
    // Configure pool with a non-resolved name (ie: not 'default')
    val pool = virtualPool("default").copy(timeouts = poolTimeout)

    // Update the topology
    core.services.head.update(pool)

    // Send a simple request and wait briefly for a repsonse
    val get = HttpRequest(HttpMethods.GET, Uri("http://localhost:8080/"))
    val response = Await.result(send(get), 5 seconds)

    response.status should be (StatusCodes.ServiceUnavailable)
    response.entity.asString should include ("Unable to connect to")
  }

  // it should "get a 503 repsonse to an invalid pool" in {


  // Also test with existing connection but with bad health
}