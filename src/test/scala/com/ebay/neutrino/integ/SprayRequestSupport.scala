package com.ebay.neutrino.integ

import akka.actor.ActorSystem
import akka.io.IO
import akka.util.Timeout
import spray.can.Http
import spray.http.{HttpRequest, HttpResponse}

/**
 * Support mixin for ActorSystem/Spray functionality.
 */
trait SprayRequestSupport {

  import akka.pattern.ask
  import scala.concurrent.duration._

  implicit val system = ActorSystem()
  implicit val timeout: Timeout = 15 seconds

  // Send the request provided and map back to a response
  def send(request: HttpRequest) = (IO(Http) ? request).mapTo[HttpResponse]

}