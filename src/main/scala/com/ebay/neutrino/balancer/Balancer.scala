package com.ebay.neutrino.balancer

import com.ebay.neutrino.{NeutrinoRequest, NeutrinoNode}
import com.ebay.neutrino.config.BalancerSettings


trait Balancer {

/*
  // Expose our scheduler's endpoint-statistics
  def statistics: Traversable[(ChannelId, EndpointStatistics)]

  // Update the status of an endpoint on completion of a response
  def update(capacity: Capacity)

  // Add a new endpoint to our set
  def register(endpoint: ChannelId)

  // Remove an endpoint from our set
  def remove(endpoint: ChannelId)
*/


  // Re(set) the current membership of the load-balancer
  def rebuild(members: Array[NeutrinoNode])

  // Assign an endpoint for request processing
  def assign(request: NeutrinoRequest): Option[NeutrinoNode]

  // Release an endpoint from request processing
  def release(request: NeutrinoRequest, node: NeutrinoNode)
}

/**
 *
 * TODO - add agent support for synchronous read/asynchronous update
 *
 * Ensure class are available in the system
 *  import scala.reflect.runtime.{universe => ru}
 *  lazy val mirror = ru.runtimeMirror(classOf[Scheduler].getClassLoader)
 *  val schedulerType = mirror.classSymbol(schedulerClass).toType
 *  val scheduler = mirror.create(schedulerType)
 */
object Balancer {

  import com.ebay.neutrino.NeutrinoLifecycle._

  /**
   * Select a load-selection mechanism.
   * @param settings
   */
  def apply(settings: BalancerSettings): Balancer =
    // Resolve a constructor
    settings.config match {
      case Some(config) => (create[Balancer](settings.clazz, config) orElse create[Balancer](settings.clazz)).get
      case None         => (create[Balancer](settings.clazz)).get
    }
}