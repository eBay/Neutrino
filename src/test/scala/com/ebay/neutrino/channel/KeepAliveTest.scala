package com.ebay.neutrino.channel

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.ebay.neutrino.{EchoIntegrationTest, ProxyIntegrationTest}
import org.scalatest._
import spray.can.Http
import spray.http._

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Parse unit-test for Neutrino-configuration
 *
 * Note - needs echo-server running. TODO fix this...
 *
 * Notes:
 * - we currently use Apache HttpComponents async-client, which is basically our 3rd choice
 * - tried using HttpUnit but too textual, bad API, and no future/async support
 * - tried using Spray.io; very easy to use but no HTTP 1.0
 * - could have used Netty async but setup difficult and would like to use something else
 *    for compliancy reasons
 */
class KeepAliveTest extends FlatSpec with ProxyIntegrationTest with EchoIntegrationTest with Matchers {
  behavior of "KeepAlive integration support"

  // Default requests
  override val configurationFile = "proxy-echo-test.conf"

  implicit val system = ActorSystem()
  implicit val timeout: Timeout = 15 seconds
  val Get = HttpRequest(HttpMethods.GET, Uri("http://localhost:8080/someurl"))


  def send(request: HttpRequest) = (IO(Http) ? request).mapTo[HttpResponse]


  it should "successfully send a single HTTP 1.1 GET" in {
    val request  = Get
    val response = Await.result(send(request), 2 seconds)

    //response.status should be (StatusCodes.OK)
    //response.protocol should be (HttpProtocols.`HTTP/1.1`)
    //response.header[HttpHeaders.Connection] shouldBe a [Some[HttpHeader]]
    //response.headers find (_ == HttpHeaders.Connection) .hasKeepAlive should be (false)
  }

  it should "successfully send a single HTTP 1.1 GET with connection-close" in {
    val start    = System.currentTimeMillis
    val request  = Get.copy(headers = List(HttpHeaders.Connection("close")))
    val response = Await.result(send(request), 2 seconds)

    response.status should be (StatusCodes.OK)
    response.protocol should be (HttpProtocols.`HTTP/1.1`)
    response.header[HttpHeaders.Connection] should be (Some(HttpHeaders.Connection("close")))
  }

  it should "successfully send a single HTTP 1.0 GET" in {
    val request  = Get.copy(protocol=HttpProtocols.`HTTP/1.0`)
    val response = Await.result(send(request), 2 seconds)

    // Regardless of Client:1.0, server should support 1.1
    response.status should be (StatusCodes.OK)
    response.protocol should be (HttpProtocols.`HTTP/1.1`)
    // response.headers should not contain keep-alive
  }

  it should "successfully send a single HTTP 1.0 GET w. keepalive" in {
    val request  = Get.copy(protocol=HttpProtocols.`HTTP/1.0`, headers = List(HttpHeaders.Connection("Keep-Alive")))
    val response = Await.result(send(request), 2 seconds)

    // Regardless of Client:1.0, server should support 1.1
    response.status should be (StatusCodes.OK)
    response.protocol should be (HttpProtocols.`HTTP/1.1`)
    response.header[HttpHeaders.Connection] shouldBe a [Some[_]]
    response.header[HttpHeaders.Connection].get.hasKeepAlive should be (true)
  }
}
