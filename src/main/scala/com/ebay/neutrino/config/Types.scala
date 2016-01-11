package com.ebay.neutrino.config

import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.handler.codec.http.HttpResponse

/**
 * This file provides enumeration types.
 *
 */
// Transport protocol support
sealed trait Transport

object Transport extends StrictLogging {

  // Subtype
  case object HTTP  extends Transport { override val toString = "http" }
  case object HTTPS extends Transport { override val toString = "https" }
  case object ZMQ   extends Transport { override val toString = "zeromq" }
  case class Unknown(value: String) extends Transport { override val toString = value }

  // Resolve a transport-protocol type from the string provided.
  def apply(protocol: String): Transport =
    protocol.toLowerCase match {
      case "http" => HTTP
      case "https" | "ssl" => HTTPS
      case "zmq"  => ZMQ
      case lc =>
        logger.error("Protocol '{}' not supported", protocol)
        Unknown(protocol)
    }
}



/**
 * Encapsulated request-completion status enumeration/bucketing support.
 */
sealed trait CompletionStatus

object CompletionStatus {

  // Supported types
  case object Status2xx  extends CompletionStatus
  case object Status4xx  extends CompletionStatus
  case object Status5xx  extends CompletionStatus
  case object Other      extends CompletionStatus
  case object Incomplete extends CompletionStatus

  // Extract/bucket a response
  def apply(response: Option[HttpResponse]): CompletionStatus =

    (response map (_.status.code / 100) getOrElse 0) match {
      case 0 => Incomplete
      case 2 => Status2xx
      case 4 => Status4xx
      case 5 => Status5xx
      case _ => Other
    }
}

// Heath-state types
sealed trait HealthState
object HealthState {

  // Supported types
  case object Healthy extends HealthState
  case object Unhealthy extends HealthState
  case object Probation extends HealthState
  case object Maintenance extends HealthState
  case object Error extends HealthState
  case object Unknown extends HealthState
  case class  Other(value: String) extends HealthState
}


/**
 * Data wrapper classes
 */
case class Host(host: String, port: Int)
{
  require(port >= 0 && (port >> 16) == 0, "Illegal port: " + port)
  require(host == host.toLowerCase, "Host should be normalized (to lower case)")

  def hasPort() = port > 0
}

object Host {

  // Create a host-object out of the message provided
  def apply(host: String): Host =
    host.split(":") match {
      case Array(host)       => Host(host, 0)
      case Array(host, port) => Host(host, port.toInt)
      case array             => throw new IllegalArgumentException(s"Bad split for host $host: $array")
    }
}