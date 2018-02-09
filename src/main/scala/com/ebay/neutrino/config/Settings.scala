package com.ebay.neutrino.config

import java.lang.reflect.Constructor
import java.net.URI

import com.ebay.neutrino.NeutrinoLifecycle
import com.ebay.neutrino.balancer.{Balancer, LeastConnectionBalancer, RoundRobinBalancer, WeightedRoundRobinBalancer}
import com.ebay.neutrino.metrics.HealthMonitor
import com.ebay.neutrino.util.Utilities._
import com.typesafe.config.Config

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}


/**
 * Extension providing all settings available to the application:
 * - HttpServer/Frontend Server
 * - LoadBalancer
 * - Daemon
 * - Echo-Server
 *
 * Note that these settings do not include the 'runtime' pool configuration. (LoadBalancer)
 * They need to be populated separately (as they can be set dynamically)
 */
case class NeutrinoSettings(
  interfaces:           Seq[ListenerSettings],
  lifecycleListeners:   Seq[NeutrinoLifecycle],
  defaultTimeouts:      TimeoutSettings,
  supervisorThreadCount:Int,
  workerThreadCount:    Int
)


/**
 * Core Load-Balancer settings
 */
object NeutrinoSettings {
  import com.ebay.neutrino.config.Configuration._

  import scala.collection.JavaConversions._

  // Try a one-parameter ctor for settings
  def createLifecycle(lifecycleClass: Class[_ <: NeutrinoLifecycle], c: Config): Option[NeutrinoLifecycle] =
    constructor(lifecycleClass.getConstructor(classOf[Config])) map (_.newInstance(c))

  // Try a default (no-param) setting
  def createLifecycle(lifecycleClass: Class[_ <: NeutrinoLifecycle]): Option[NeutrinoLifecycle] =
    constructor(lifecycleClass.getConstructor()) map (_.newInstance())

  // Resolve the constructor provided, skipping unavailable ones
  def constructor[T](ctor: => Constructor[T]): Option[Constructor[T]] =
    Try(ctor) match {
      case Success(ctor) => Option(ctor)
      case Failure(x: NoSuchMethodException) => None
      case Failure(x) => throw x
    }


  // Empty configuration for mocking/empty setup
  val Empty = NeutrinoSettings(Seq(), Seq(), TimeoutSettings.Default, 1, 4)


  // Create a new global LoadBalancer application setting object.
  // For now, just support 'default'
  def apply(cfg: Config) = {
    // Load config-wide defaults
    val listener = cfg getConfig("listener")
    val classes: List[Class[NeutrinoLifecycle]] = cfg getOptionalClassList "initializers"
    val instances = classes flatMap (cls => createLifecycle(cls, cfg) orElse createLifecycle(cls))

    new NeutrinoSettings(
      cfg getConfigList "listeners" map (_ withFallback listener) map (ListenerSettings(_)),
      instances,
      cfg getTimeouts "timeout",
      cfg getInt  "supervisorThreadCount",
      cfg getInt  "workerThreadCount"

    )
  }

}


/**
 * Daemon/End-Node Settings
 *
 *
 */
case class DaemonSettings(endpoint: URI)
{
  val host = endpoint.validHost
  val port = endpoint.validPort(80)
  val isSecure = endpoint.isSecure()
}

object DaemonSettings {

  def apply(c: Config): DaemonSettings =
    DaemonSettings(
      URI.create(c getString "endpoint")
    )
}



// Representation of a Health Monitor/Settings
case class HealthSettings(monitorType: String, path: URI, monitor: Option[HealthMonitor]) {
  require(path.getHost == null && path.getPort == -1 && path.getAuthority == null,
    "URL Path/Port/Authority not supported - only path should be set")

}

object HealthSettings {
  import com.ebay.neutrino.config.Configuration._

  // Try a one-parameter ctor for settings
  def createMonitor(monitorClass: Class[_ <: HealthMonitor], c: Config): Try[HealthMonitor] =
    Try(monitorClass.getConstructor(classOf[HealthSettings]).newInstance(c))

  // Try a default (no-param) setting
  def createMonitor(monitorClass: Class[_ <: HealthMonitor]): Try[HealthMonitor] =
    Try(monitorClass.newInstance())


  // HealthMonitor configuration factory
  def apply(config: Config): HealthSettings = {
    val clazz: Option[Class[HealthMonitor]] = config getOptionalClass "monitor-class"

    HealthSettings(
      "type",
      config getUri "url",
      clazz flatMap (cls => (createMonitor(cls, config) orElse createMonitor(cls)).toOption)
    )
  }
}



case class TimeoutSettings(
  readIdle:           Duration,
  writeIdle:          Duration,
  writeCompletion:    Duration,
  requestCompletion:  Duration,
  sessionCompletion:  Duration,
  connectionTimeout:  Duration
)
{
  require(!readIdle.isFinite() || readIdle != Duration.Zero)
  require(!writeIdle.isFinite() || writeIdle != Duration.Zero)
  require(!writeCompletion.isFinite() || writeCompletion != Duration.Zero)
  require(!requestCompletion.isFinite() || requestCompletion != Duration.Zero)
  require(!sessionCompletion.isFinite() || sessionCompletion != Duration.Zero)
  require(!sessionCompletion.isFinite() || sessionCompletion != Duration.Zero)
}


object TimeoutSettings {
  import com.ebay.neutrino.config.Configuration._

  import scala.concurrent.duration.Duration.Undefined
  import scala.concurrent.duration._

  val Default = TimeoutSettings(60 seconds, 60 seconds, 10 seconds, 2 minutes, 2 minutes, 5 seconds)
  val NoTimeouts = TimeoutSettings(Undefined, Undefined, Undefined, Undefined, Undefined, Undefined)

  def apply(config: Config): TimeoutSettings = TimeoutSettings(
    config getOptionalDuration "read-idle-timeout"  getOrElse Undefined,
    config getOptionalDuration "write-idle-timeout" getOrElse Undefined,
    config getOptionalDuration "write-timeout"      getOrElse Undefined,
    config getOptionalDuration "request-timeout"    getOrElse Undefined,
    config getOptionalDuration "session-timeout"    getOrElse Undefined,
    config getOptionalDuration "connection-timeout" getOrElse Undefined
  )
}


import scala.language.existentials
case class BalancerSettings(clazz: Class[_ <: Balancer], config: Option[Config])
{
  require(NeutrinoLifecycle.hasConstructor(clazz),
    "Balancer class must expose either a no-arg constructor or a Config constructor.")
}

object BalancerSettings {

  // Static balancers available
  val RoundRobin = BalancerSettings(classOf[RoundRobinBalancer], None)
  val WeightedRoundRobin = BalancerSettings(classOf[WeightedRoundRobinBalancer], None)
  val LeastConnection = BalancerSettings(classOf[LeastConnectionBalancer], None)

  // Default to Round-Robin scheduling with no additional configuation
  val Default = RoundRobin

  /**
   * Select a load-selection mechanism.
   *
   * Attempt to resolve class for scheduler.
   * If it's known, use the class directly. Otherwise, attempt to resolve.
   *
   * @param balancer
   */
  def apply(balancer: String): BalancerSettings =
    balancer toLowerCase match {
      case "rr" | "round-robin" => RoundRobin
      case "wrr" | "weighted-round-robin" => WeightedRoundRobin
      case "lc" | "least-connection" => LeastConnection
      case className =>
        BalancerSettings(
          Class.forName(balancer).asInstanceOf[Class[Balancer]],
          None
        )
    }

  // def apply(config: Config): BalancerSettings = ...
}