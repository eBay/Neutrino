package com.ebay.neutrino

import com.ebay.neutrino.config.{HealthState, TimeoutSettings, VirtualServer}
import com.ebay.neutrino.handler.ops.{ChannelStatisticsHandler, ChannelTimeoutHandler, NeutrinoAuditHandler}
import com.ebay.neutrino.handler.{NeutrinoClientDecoder, NeutrinoClientHandler}
import com.ebay.neutrino.metrics.{Instrumented, Metrics, MetricsKey}
import com.ebay.neutrino.util.{DifferentialStateSupport, Utilities}
import com.typesafe.scalalogging.slf4j._
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel._
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.HttpRequestEncoder

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

/**
 * A stateful runtime wrapper around a VirtualServer, encapsulating all the state
 * necessary to manage a downstream node (server).
 *
 * Note that we have an asymmetric relationship with the LoadBalancer component;
 *  - The Pool delegates allocate() calls to the Balancer
 *  - The Balancer is responsible for selecting and calling connect() on the node to
 *    establish the connection
 *  - The Channel's close-listener calls release() on the node on completion
 *  - The Node notifies the balancer of the release.
 *
 * Otherwise, the channel has to maintain an unnecessary link back to the balancer
 * that assigned it, and the load-balancer in turn has to maintain channel->node
 * syncronized state.
 *
 * TODO convert to cached InetAddress to speed up resolution
 * TODO implement available this stack using a mature concurrent manager
 */
case class NeutrinoNode(pool: NeutrinoPool, settings: VirtualServer)
  extends ChannelFutureListener
  with StrictLogging
  with Instrumented
{
  import Utilities._
  import com.ebay.neutrino.handler.NeutrinoClientHandler._
  import com.ebay.neutrino.util.AttributeSupport._

  // Member datum
  val initializer = new NeutrinoNodeInitializer(this)
  val available   = new java.util.concurrent.ConcurrentLinkedDeque[Channel]()
  val allocated   = new TrieMap[ChannelId, Channel]()


  /**
   * Update the node settings.
   */
  def update(setting: VirtualServer) = {
    // Update current health
    if (settings.healthState != HealthState.Unknown) settings.healthState = settings.healthState
  }

  /**
   * Make the connection attempt.
   *
   * The challenge here is that we want to inject a fully-formed EndpointHandler
   * into the initial pipeline, which requires an EndpointConnection object.
   * Unfortunately, this object is created by the caller only after the
   * connect has completed.
   */
  import pool.service.context
  def connect(): Future[Channel] =
    Option(available.poll()) match
    {
      case None =>
        logger.info("Initializing fresh endpoint")
        Metrics.PooledCreated.mark()
        initializer.connect()

      case Some(endpoint) if (endpoint.isAvailable()) =>
        logger.info("Assigning existing endpoint")
        Metrics.PooledAssigned.mark()
        Future.successful(endpoint)

      case Some(endpoint) if (endpoint.isActive()) =>
        logger.info("Existing endpoint unavailable (inconsistent state); initializing fresh endpoint")
        Metrics.PooledRecreated.mark()
        endpoint.close()
        connect()

      case Some(endpoint) =>
        logger.info("Existing endpoint inactive; initializing fresh endpoint")
        Metrics.PooledRecreated.mark()
        endpoint.close()
        connect()
    }


  /**
   * Make the connection attempt, using pooled-connection support.
   *
   * The challenge here is that we want to inject a fully-formed EndpointHandler
   * into the initial pipeline, which requires an EndpointConnection object.
   * Unfortunately, this object is created by the caller only after the
   * connect has completed.
   *
   * TODO convert to cached InetAddress to speed up resolution
   */
  def resolve(): Future[Channel] = {
    // Attempt to resolve, rejecting inactive/closed connections
    // Do we need to explicitly clean them up?
    val channel = connect()

    // Cache the channel's allocation
    channel andThen {
      case Success(channel) =>
        require(!allocated.contains(channel.id), s"Attempt to assign an already-allocated endpoint $channel")
        allocated(channel.id) = channel
        channel.statistics.allocations.incrementAndGet()

      case Failure(ex) =>
        Metrics.PooledFailed += 1
        logger.info("Channel connection failed with {}", ex.getMessage)
    }
  }


  /**
   * Release the allocated endpoint back to the 'allocated' list.
   *
   * TODO support closing connection if too many cached
   * TODO Retire overused connections
   */
  def release(channel: Channel) =
    // Try and resolve existing
    allocated remove(channel.id) match
    {
      case Some(ch) if (channel.isReusable()) =>
        // We want to make sure this is a valid; push through an empty write to force any pending closes
        channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(this)

      case Some(ch) if (channel.isActive()) =>
        // Downstream channel has requested a close (ie: keep-alive false). Close and release now.
        channel.close()
        Metrics.PooledClosed += 1
        logger.info("Releasing and closing endpoint {}", channel)

      case Some(ch) =>
        Metrics.PooledClosed += 1
        logger.info("Releasing closed endpoint {}", channel)

      case None =>
        metrics.counter(MetricsKey.PoolReleased,  "mismatch") += 1
        logger.warn("Attempted to return a channel which was not allocated: {}", channel.toStringExt)
    }


  /**
   * Handle retry/release priming operations.
   * @param future
   */
  override def operationComplete(future: ChannelFuture): Unit =
    if (future.isSuccess) {
      Metrics.PooledRetained += 1
      available.push(future.channel)
      logger.info("Releasing endpoint {}", future.channel)
    }
    else {
      Metrics.PooledClosed += 1
      logger.info("Flush failed; closing endpoint {}", future.channel)
    }



  // TODO - implement correct/custom shutdown behavior
  def shutdown() = {}

}


/**
 * Downstream connection initializer.
 *
 * @param node
 */
class NeutrinoNodeInitializer(node: NeutrinoNode)
  extends ChannelInitializer[SocketChannel]
  with ChannelFutureListener
  with Instrumented
{
  import com.ebay.neutrino.metrics.Metrics._
  import com.ebay.neutrino.util.AttributeSupport._
  import com.ebay.neutrino.util.Utilities._

  // Customized timeouts for server-clients; use only the channel-specific values and ensure a
  // valid connection-timeout
  val timeouts = {
    val defaults = node.pool.settings.timeouts
    val connect  = defaults.connectionTimeout

    defaults.copy(
      requestCompletion = Duration.Undefined,
      sessionCompletion = Duration.Undefined,
      connectionTimeout = if (connect.isFinite) connect else TimeoutSettings.Default.connectionTimeout
    )
  }

  val bootstrap = new Bootstrap()
    .channel(classOf[NioSocketChannel])
    .group(node.pool.service.core.workers)
    .option[java.lang.Boolean](ChannelOption.TCP_NODELAY, true)
    .option[java.lang.Integer](ChannelOption.CONNECT_TIMEOUT_MILLIS, timeouts.connectionTimeout.toMillis.toInt)
    .handler(this)

  // Static handlers
  val timeout = new ChannelTimeoutHandler(timeouts)
  val stats   = new ChannelStatisticsHandler(false)
  val audit   = new NeutrinoAuditHandler()


  // Framing decoders; extract initial HTTP connect event from HTTP stream
  protected override def initChannel(channel: SocketChannel): Unit = {
    val client = new NeutrinoClientHandler()

    // Configure our codecs
    channel.pipeline.addLast(timeout)
    channel.pipeline.addLast(stats)
    channel.pipeline.addLast(new HttpRequestEncoder())
    channel.pipeline.addLast(new NeutrinoClientDecoder(client.queue))
    channel.pipeline.addLast(audit)
    channel.pipeline.addLast(client)

    // Update our downstream metrics
    Metrics.DownstreamTotal.mark
    Metrics.DownstreamOpen += 1

    // Prime the statistics object and hook channel close for proper cleanup
    channel.statistics
    channel.closeFuture().addListener(this)
  }


  /**
   * Connect to the downstream.
   * @return
   */
  def connect() = bootstrap.connect(node.settings.socketAddress).future

  /**
   * Customized channel-close listener for Neutrino IO channels.
   * Capture and output IO data.
   */
  override def operationComplete(future: ChannelFuture) = {
    DownstreamOpen -= 1
  }
}


/**
 * Implementation of a state wrapper around a NeutrinoNode
 * (and its contained VirtualServer settings).
 *
 * We implement state in a TrieMap to provide concurrent access support.
 * An alternative would be to restrict access to this class and mediate concurrent
 * access externally.
 */
class NeutrinoNodes(pool: NeutrinoPool)
  extends DifferentialStateSupport[String, VirtualServer] with StrictLogging
{
  // Cache our nodes here (it should make it easier to cleanup on lifecycle here)
  val nodes = new TrieMap[String, NeutrinoNode]()

  // Extract just the node-values, in read-only mode
  def apply() = nodes.readOnlySnapshot.values

  // Required methods
  override protected def key(v: VirtualServer): String = v.id

  override protected def addState(settings: VirtualServer) =
    nodes put (key(settings), new NeutrinoNode(pool, settings))

  override protected def removeState(settings: VirtualServer) =
    nodes remove(key(settings)) map { node => node.shutdown() }

  // Update the server's status
  override protected def updateState(pre: VirtualServer, post: VirtualServer) =
    nodes get (key(pre)) match {
      case Some(node) => node.update(post)
      case None => logger.warn("Unable to resolve a node for key {}", key(pre).toString)
    }

}