package com.ebay.neutrino

import java.net.NoRouteToHostException

import com.ebay.neutrino.balancer.Balancer
import com.ebay.neutrino.channel.{NeutrinoSession, NeutrinoService}
import com.ebay.neutrino.config.{Transport, VirtualPool}
import com.ebay.neutrino.util.DifferentialStateSupport
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.channel.Channel

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

/**
 * An extremely simple shim between a pool 'implementation' and the underlying
 * configuration as imported from our settings.
 *
 * Good model for this...
 * @see https://github.com/twitter/commons/tree/master/src/java/com/twitter/common/net/loadbalancing
 *
 *
 * State management:
 * - This object requires a set of 'default' settings, both mutable and immutable
 *
 * - A caller can update the mutable settings using the update() method, which will
 *   replace the current settings.
 *
 * - The following properties are immutable and attempts to update will cause an
 *   exception:
 *    - id
 *    - protocol
 *    - balancer settings (in particular, balancer-type currently can't be changed)
 *
 * - The following properties are mutable and may be changed with update()
 *    - servers
 *    - addresses
 *    - others
 */
class NeutrinoPool(val service: NeutrinoService, defaultSettings: VirtualPool) extends StrictLogging
{
  import com.ebay.neutrino.util.AttributeSupport._
  import service.context

  // Active settings; prevent external change
  private var currentSettings = defaultSettings

  // Create our load-balancing algorithm for this pool
  val balancer = Balancer(settings.balancer)

  // Cache our nodes here (it should make it easier to cleanup on lifecycle here)
  val nodes = new NeutrinoNodes(this)

  // Complete initialization of settings
  update(defaultSettings)


  // Settings accessor; prevent callers from changing the active-settings
  def settings: VirtualPool = currentSettings

  /**
   * HTTP EndpointResolver support
   * Register a new incoming request, and attempt to aøllocate a new peer endpoint
   *
   * TODO resolve an endpoint-service for this connect...
   * TODO implement load-balancing ...
   */
  def resolve(request: NeutrinoRequest): Future[Channel] =
  {
    def failed()  = Future.failed(new NoRouteToHostException(s"Unable to connect to ${request.uri}"))
    val assigned  = balancer.assign(request)
    val allocated = assigned map (node => node.resolve map ((node, _)))
    val channel   = allocated getOrElse failed()

    // On success, store our components in the request
    channel map { case (node, channel) =>
      request.node = node
      request.balancer = balancer
      request.downstream = channel
      channel.session = request.session
      channel
    }
  }

  def release(request: NeutrinoRequest) = {
    require(request.balancer.isEmpty || request.balancer.get == balancer, "Request balancer belongs to different pool")

    (request.node, request.downstream) match {
      case (Some(node), Some(channel)) =>
        logger.info("Response completed for {} - releasing downstream.", request)

        // Clear out downstream and request
        channel.session = None
        request.node = None
        request.balancer = None
        request.downstream = None

        // Release node first (balancer release can trigger additional pull from node)
        node.release(channel)
        balancer.release(request, node)

      case (None, None) =>
        // Channel/Node was never allocated (connection was never successful). Nothing to return.

      case (node, channel) =>
        logger.warn("Attempted to release a request with invalid node and/or downstream channel set: {}, {}", node, channel)
    }
  }


  /**
   * Update the pool's settings with the provided.
   */
  def update(settings: VirtualPool) =
  {
    // Validate incoming settings against our default
    require(settings.id == defaultSettings.id)
    require(settings.protocol == defaultSettings.protocol)
    require(settings.balancer == defaultSettings.balancer)

    // Update the cached settings
    currentSettings = settings

    // Diff the current nodes and updated
    nodes.update(settings.servers:_*)

    // After everything is updated, push the new config to the load-balancer
    // TODO can this call be more efficient? We already know the size
    balancer.rebuild(nodes().toArray)
  }


  /**
   * Handle pool shutdown; peform required quiescence.
   * TODO; make better
   */
  def shutdown() = {}


  /**
   *
   */
  override def toString() = super.toString()
}


case class NeutrinoPoolId(id: String, transport: Transport) {
  require(!id.contains("::"), "ID should not contain the :: characters")
  override val toString = id+"::"+transport
}

object NeutrinoPoolId {

  // Create a neutrino pool ID out of a serialized string representation
  def apply(id: String): NeutrinoPoolId =
    id.split("::") match {
      case Array(poolid, trans) => NeutrinoPoolId(poolid, Transport(trans))
      case _ => throw new IllegalArgumentException(s"Unable to extract NeutrinoPoolId from $id")
    }

  // Create a neutrino pool ID out of id/session
  def apply(id: String, session: NeutrinoSession): NeutrinoPoolId =
    NeutrinoPoolId(id, session.service.settings.protocol)

}


/**
 * Implementation of a state wrapper around a NeutrinoPool
 * (and its contained VirtualPool settings).
 *
 * We implement state in a TrieMap to provide concurrent access support.
 * An alternative would be to restrict access to this class and mediate concurrent
 * access externally.
 */
class NeutrinoPools(val service: NeutrinoService)
  extends DifferentialStateSupport[NeutrinoPoolId, VirtualPool]
  with Iterable[VirtualPool]
  with StrictLogging
{
  val pools = new TrieMap[NeutrinoPoolId, NeutrinoPool]()


  // Required methods
  @inline override protected def key(pool: VirtualPool): NeutrinoPoolId =
    NeutrinoPoolId(pool.id, pool.protocol)

  override protected def addState(settings: VirtualPool) = {
    // Initialize the startup nodes and configure the balancer
    val pool = new NeutrinoPool(service, settings)
    logger.info("Configuring new pool {}", pool)
    pools.put(key(settings), pool)
  }

  override protected def removeState(settings: VirtualPool) =
    pools.remove(key(settings)) map { pool =>
      logger.info("Removing existing pool {}", pool)
      pool.shutdown()
    }

  override protected def updateState(pre: VirtualPool, post: VirtualPool) = {
    pools.get(key(pre)) match {
      case Some(pool) =>
        logger.info("Updating existing pool {}", pool)
        pool.update(post)

      case None =>
        // Pool was removed in the time between calculation of the state and this
        logger.error("Error upsting existing pool {} - was removed concurrently.", pre.id)
    }
  }


  /**
   * Public accessor for NeutrinoPool.
   * Callers should use iterator() unless the underlying NeutrinoPools are required.
   * This is immutable so changes to the underlying collection will not be visible.
   */
  def apply() = pools.readOnlySnapshot().values

  /**
   * Public accessor for VirtualPool settings.
   * This is immutable so changes to the underlying collection will not be visible.
   */
  override def iterator: Iterator[VirtualPool] =
    pools.readOnlySnapshot().values.iterator map (_.settings)
}