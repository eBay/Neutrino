package com.ebay.neutrino.channel

import com.ebay.neutrino.{NeutrinoServiceInitializer, _}
import com.ebay.neutrino.config._
import com.ebay.neutrino.metrics.Instrumented
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.channel.socket.nio.NioServerSocketChannel

import scala.concurrent.Future

/**
 * Channel management service/wrapper around incoming VIPs
 *
 * Incoming channel needs to open like this:
 * 1) New Channel
 * 2) Figure out remote endpoint
 * 3) If Endpoint selected, create endpoint handler last
 * 4) If endpoint is not proxy-only, create pipeline handler
 */
@Sharable
class NeutrinoService(val core: NeutrinoCore, val settings: ListenerSettings)
  extends StrictLogging
  with Instrumented
{
  implicit val context = core.context

  // Initialized connections
  private[this] var incomingConnections = Map[VirtualAddress, Future[Channel]]()

  // Configure Listeners
  private[this] var established = Map[ListenerAddress, Future[ServerChannel]]()

  // Shared handlers
  val factory  = new NeutrinoServiceInitializer(this)
  val pools    = new NeutrinoPools(this)


  // Listen on all configured addresses.
  // These will be all-successful or all-failure.
  def listen(): Seq[(ListenerAddress, Future[Channel])] = {
    require(established.isEmpty, "Service already started; can't listen() twice")

    // Initialize individual ports
    val addresses = settings.addresses map ((_, settings))

    addresses collect { case (address, settings) =>
      require(!established.isDefinedAt(address), s"Address $address already defined; can't initialize twice")

      // Create a service to handle the addresses
      val pair = (address -> listen(address))

      // Build a map-tuple out of this
      established += pair
      pair
    }
  }


  def shutdown() = {
    // TODO close established
  }


  /**
   * Update the service topology/configuration.
   *
   * We prefilter to remove any pools that are configured for an incompatible protocol
   * as our own.
   */
  def update(pools: VirtualPool*) =
  {
    // Split out the relevant (by protocol)
    val relevant = pools filter (pool => pool.protocol == settings.protocol)

    // Update the internal pool-state
    this.pools.update(relevant:_*)
  }


  /**
   * Make the connection attempt.
   *
   * The challenge here is that we want to inject a fully-formed EndpointHandler
   * into the initial pipeline, which requires an EndpointConnection object.
   * Unfortunately, this object is created by the caller only after the
   * connect has completed.
   *
   * TODO handle bind() failure; should probably kill the server ASAP
   */
  def listen(address: VirtualAddress): Future[ServerChannel] = {
    import com.ebay.neutrino.util.Utilities._

    logger.info("Creating listener on {}", address)

    // Do the socket-bind
    val socket = address.socketAddress
    val channel = new ServerBootstrap()
      .channel(classOf[NioServerSocketChannel])
      .group(core.supervisor, core.workers)
      .option[java.lang.Integer](ChannelOption.SO_BACKLOG, 512)
      .childOption[java.lang.Boolean](ChannelOption.TCP_NODELAY, true)
      .childHandler(factory)
      .bind(socket)
      .future()
      .asInstanceOf[Future[ServerChannel]]

    logger.info(s"Starting HTTP on port $socket")

    // Cache the bound socket (not yet complete)
    incomingConnections += (address -> channel)
    channel
  }


  /** Delegate the resolve() request to the connection's pools, using any available resolvers */
  def resolvePool(request: NeutrinoRequest): Option[NeutrinoPool] = {
    // Iterate any available resolvers, matching the first resolved pool
    settings.poolResolvers.toStream flatMap (_.resolve(pools, request)) headOption
  }
}


/**
 * Implementation of a state wrapper around a set of NeutrinoServices
 *
 * Should we allow dynamic configuration??
 * handle quiescence...
 *
 * def configure(listeners: ListenerSettings*) = {
 *   listeners map (new NeutrinoService(this, _))
 * }
 *
 * ?? Should we check for duplicate ports?
 */
class NeutrinoServices(val core: NeutrinoCore) extends Iterable[NeutrinoService] with StrictLogging {

  val listeners: Map[ListenerSettings, NeutrinoService] =
    core.settings.interfaces map (setting => setting -> new NeutrinoService(core, setting)) toMap


  // Get service by port
  def forPort(port: Int): Option[NeutrinoService] =
    listeners find (_._1.sourcePorts.contains(port)) map (_._2)

  // Return an iterator over our configured services
  override def iterator: Iterator[NeutrinoService] = listeners.values.iterator

}
