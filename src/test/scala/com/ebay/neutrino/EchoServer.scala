package com.ebay.neutrino

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.io.IO
import com.ebay.neutrino.util.Random
import com.typesafe.config.{Config, ConfigFactory}
import spray.can.Http
import spray.http._

import scala.concurrent.duration._


object EchoServer extends App {

  // Extract port from args, if provided
  val port = if (args.size > 0) args(0).toInt else 8081

  // Load our configuration from file and merge in the port parameter
  val config = ConfigFactory.parseString(s"echo-server.port = $port") withFallback ConfigFactory.load("echo.conf")
  val system = ActorSystem("echo-server", config)
  system.actorOf(Props[EchoServer], "echo-server")
}


class EchoServer extends Actor with ActorLogging {
  import scala.language.implicitConversions

  implicit val system = context.system
  val startup  = System.currentTimeMillis
  val settings = EchoServerSettings(system)

  //Use the system's dispatcher as ExecutionContext
  import system.dispatcher

  // Register connection service
  IO(Http) ! Http.Bind(self, interface = settings.host, port = settings.port)

  /**
   *
   * Wire our message handling:
   * - Connections: server connection-lifecycle handling (including timeouts)
   * - Requests:    request-entity dispatch and handling
   * - Other:       error handling
   *
   */
  def receive = {
    case Http.Bound(address) =>
      // Startup our connection-handling
      println (s"Server bound on $address")

    case _:Http.Connected =>
      sender ! Http.Register(self)

    case HttpRequest(_, uri, _, _, _) =>
      // Extract configuration
      val response = HttpResponse(status = StatusCodes.OK, entity = "Echo!\r\n")
      val latency  = settings.latency

      // Introduce an appropriate delay and send back a result
      system.scheduler.scheduleOnce(latency, sender, response)
      log.info ("Sending HttpResponse {}", response)

    case Timedout(request: HttpRequest) =>
      log.error ("Request timed out: {}", request)

    case msg: Http.ConnectionClosed =>
      log.debug ("Connection closed: "+msg)

    case other =>
      log.error(other.toString)
  }
}



/**
 * EchoSettings
 *
 */
case class EchoServerSettings(host: String, port: Int, random: Boolean, duration: FiniteDuration)
  extends Extension
{
  def latency = random match {
    case false => duration
    case true  => Random.nextMillis(duration)
  }
}

object EchoServerSettings {

  def apply(c: Config): EchoServerSettings = EchoServerSettings(
    c getString "host",
    c getInt "port",
    c getBoolean "random",
    c getDuration("duration", TimeUnit.MILLISECONDS) milliseconds
  )

  def apply(system: ActorSystem): EchoServerSettings =
    EchoServerSettings(system.settings.config getConfig "echo-server")

}