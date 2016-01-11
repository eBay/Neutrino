package com.ebay.neutrino

import com.ebay.neutrino.channel.NeutrinoServices
import com.ebay.neutrino.config._
import com.ebay.neutrino.metrics.{Instrumented, MetricsKey}
import com.ebay.neutrino.util.Utilities
import com.typesafe.config.Config
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.channel.nio.NioEventLoopGroup

import scala.concurrent.Future


/**
 * Core Balancer Component.
 *
 * Good Netty design practices:
 * @see http://normanmaurer.me/presentations/2014-facebook-eng-netty/slides.html#28.0
 *
 * Netty Read throttling:
 * @see https://groups.google.com/forum/?fromgroups=#!topic/netty/Zz4enelRwYE
 */
class NeutrinoCore(private[neutrino] val settings: NeutrinoSettings) extends StrictLogging with Instrumented {

  //implicit val ec = ExecutionContext.fromExecutor(Executors.newCachedThreadPool()newFixedThreadPool(10))
  implicit val context = scala.concurrent.ExecutionContext.Implicits.global

  // Connections (this should be externalized? We're sort of bleeding impl here)
  val services    = new NeutrinoServices(this)
  val supervisor  = new NioEventLoopGroup(settings.supervisorThreadCount)
  val workers     = new NioEventLoopGroup(settings.workerThreadCount)

  // Add some simple diagnostic metrics
  metrics.safegauge(MetricsKey.BossThreads) { supervisor.executorCount }
  metrics.safegauge(MetricsKey.IOThreads)   { workers.executorCount }


  /**
   * Configure the balancer with the settings provided.
   *
   * Note - this method is depricated. Callers should (ideally) configure the underlying
   * service directly.
   *
   * @param settings
   */
  @Deprecated
  def configure(settings: LoadBalancer) =
    // Defer the configuration to the available services; they will split/remove relevant configurations
    services map (_.update(settings.pools:_*))


  /**
   * Start the balancer core's lifecycle.
   *
   * @return a future indicating success or failure of startup ports. Either all will be successful, or all will fail.
   */
  def start(): Future[_] = {
    import Utilities._

    // Before starting listeners, run the startup lifecycle event
    settings.lifecycleListeners foreach (_.start(this))

    // Register worker-completion activities on the work-groups, including Shutdown lifecycle events
    workers.terminationFuture().future() onComplete {
      // TODO make sure this is consistent with the core.shutdown()
      case result => settings.lifecycleListeners foreach (_.shutdown(NeutrinoCore.this))
    }

    // Initialize individual ports
    val listeners = services flatMap (_.listen())

    // Convert listeners to a group-future
    val future = Future.sequence(listeners.toMap.values)

    // If any of these guys fail, clean up them all
    future onFailure { case ex =>
      listeners foreach {
        case (address, channel) => channel.onFailure {
          case ex => logger.warn(s"Unable to successfully start listener on address: $address", ex)
        }
      }
      shutdown()
    }

    future
  }

  /**
   * Stop the balancer's execution, if running.
   *
   * @return a completion future, to notify completion of execution
   */
  def shutdown(): Future[_] = {
    import Utilities._

    // Shutdown services
    services map (_.shutdown())

    // Wrap Netty futures in scala futures
    val groups = Seq(supervisor.shutdownGracefully().future(), workers.shutdownGracefully().future())

    // Return when both are complete
    Future.sequence(groups)
  }

  /**
   * Resolve registered components.
   */
  def component[T <: NeutrinoLifecycle](clazz: Class[T]): Option[T] =
    settings.lifecycleListeners.find(_.getClass == clazz).asInstanceOf[Option[T]]

}


object NeutrinoCore {

  // Default constructor of balancer
  def apply(): NeutrinoCore = NeutrinoCore(Configuration.load())

  // Default constructor of balancer
  def apply(config: Config): NeutrinoCore = new NeutrinoCore(NeutrinoSettings(config))

  // Default constructor of balancer
  def apply(settings: NeutrinoSettings, lbconfig: LoadBalancer): NeutrinoCore = {
    val core = new NeutrinoCore(settings)
    core.configure(lbconfig)
    core
  }
}