package com.ebay.neutrino

import java.net.UnknownServiceException
import java.util.concurrent.TimeUnit

import com.ebay.neutrino.channel.NeutrinoSession
import com.ebay.neutrino.config.{CompletionStatus, Host}
import com.ebay.neutrino.metrics.Metrics
import com.ebay.neutrino.util.AttributeClassMap
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.buffer.ByteBuf
import io.netty.channel.{Channel, ChannelFuture}
import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.http._
import io.netty.util.ReferenceCounted
import io.netty.util.concurrent.{Future => NFuture, GenericFutureListener}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try


/**
 * Connection specifics.
 * HttpRequest-specific fields separated from the connection.
 *
 * This class is responsible for maintaining:
 * - the high-level request/response connection (ie: connected endpoints)
 * - the low-level state (ie: negotiated state, options)
 *
 *
 * Note that request-handling within this object is not immutable; making changes to
 * the enclosed request may/may not apply changes to the underlying request object.
 *
 * In deference to heap management, we elect to not make a copy on initial use.
 *
 * TODO also, HTTP Spec supposed to parse connection:: token and remove all matching connection-token headers
 */
class NeutrinoRequest(val session: NeutrinoSession, private[neutrino] val delegate: HttpRequest)
  extends AttributeClassMap
  with CachedPoolSupport
  with HttpRequest
  with GenericFutureListener[ChannelFuture]
  with StrictLogging
{
  import com.ebay.neutrino.util.AttributeSupport._
  import com.ebay.neutrino.util.HttpRequestUtils._

  // Cached pool; we cache it here so it's not exposed to cross-request
  val start = System.currentTimeMillis()
  val pool  = new PoolCache()
  val requestUri = delegate.URI()
  val requestKeepalive = HttpHeaderUtil.isKeepAlive(delegate)

  // Deferred resolvers
  lazy val host: Option[Host] = delegate.host(requestUri)

  // Cached response-data
  var response: Option[HttpResponse] = None


  /** Request completion future support.
    *
    */
  private val completePromise = session.channel.newPromise().addListener(this)

  def addListener(listener: GenericFutureListener[_ <: NFuture[_ >: Void]]) =
    completePromise.addListener(listener)

  def addListeners(listeners: GenericFutureListener[_ <: NFuture[_ >: Void]]*) =
    completePromise.addListeners(listeners:_*)


  /**
   * Process the request-completion.
   * @param future
   */
  override def operationComplete(future: ChannelFuture): Unit =
  {
    // Connection Pool/Node release
    pool.release()

    val micros = elapsed.toMicros
    Metrics.RequestsOpen.dec()
    Metrics.RequestsCompleted.update(micros, TimeUnit.MICROSECONDS)
    Metrics.RequestsCompletedType(CompletionStatus(response)).update(micros, TimeUnit.MICROSECONDS)

    logger.info("Request completed: {}", this)
  }

  /**
   * Register a new incoming request, and attempt to allocate a new peer endpoint.
   * If not established, or force is provided, renegotiate the endpoint.
   *
   * Since we can't assume that a connect attempt will be successful or will fail,
   * we actually need to defer the connection-setup to the future success.
   * In the short-term, however, we want to preserve the same deterministic method
   * return value, so we'll use the future but wait on the response (which is a
   * SORT OF BAD THING)
   *
   * TODO convert the return type to a Future[EndpointConnection]
   * TODO convert the whole typing to Scala-specific typing
   */
  def connect() = {
    // Ensure we're not already-established
    require(this.downstream.isEmpty, "Currently, downstream is reestablished for each request. Shouldn't be already set")

    // Store pool selection (we'll need to return the node back to the selected pool)
    pool() match {
      case None =>
        Future.failed(new UnknownServiceException(s"Unable to resolve pool for request."))

      case Some(pool) =>
        // If not established, or force is provided, renegotiate the endpoint
        // Grab the connection's current pool, and see if we have a resolver available
        // If downstream not available, attempt to connect one here
        logger.info("Establishing downstream to {}", pool.settings.id)
        pool.resolve(this)
    }
  }

  /**
   * Close the outstanding request.
   *
   * This is intended to be idempotic; it can be called more than once and should
   * guarantee that the close-events are only executed once.
   */
  def complete() =
    completePromise.synchronized {
      // Complete the request promise
      if (!completePromise.isDone) completePromise.setSuccess
    }


  // Calculate elapsed duration of the current request
  def elapsed = (System.currentTimeMillis-start) millis


  /** Delegate HttpRequest methods
    *
    */
  @Deprecated
  def getMethod(): HttpMethod = delegate.method()
  def method(): HttpMethod = delegate.method()
  def setMethod(method: HttpMethod): HttpRequest = { delegate.setMethod(method); this }

  @Deprecated
  def getUri(): String = delegate.uri()
  def uri(): String = delegate.uri()
  def setUri(uri: String): HttpRequest = { delegate.setUri(uri); this }

  @Deprecated
  def getProtocolVersion(): HttpVersion = delegate.protocolVersion()
  def protocolVersion(): HttpVersion = delegate.protocolVersion()
  def setProtocolVersion(version: HttpVersion): HttpRequest = { delegate.setProtocolVersion(version); this }

  @Deprecated
  def getDecoderResult(): DecoderResult = delegate.decoderResult()
  def decoderResult(): DecoderResult = delegate.decoderResult()
  def setDecoderResult(result: DecoderResult): Unit = delegate.setDecoderResult(result)

  def headers(): HttpHeaders = delegate.headers()
}


/**
 * Neutrino Pipeline only: endpoint pool for request.
 *
 * Note that this class is not synchronized; callers should provide their own
 * synchronization as required.
 */
sealed trait CachedPoolSupport { self: NeutrinoRequest =>
  import com.ebay.neutrino.util.AttributeSupport.RequestAttributeSupport


  class PoolCache {

    // Cached pool-resolver; this supports the notion of a per-request resolver
    private var pool: Option[NeutrinoPool] = None

    // Is the pool current cached?
    def isEmpty() = pool.isEmpty

    /** Delegate the resolve() request to the connection's pools */
    def resolve() = session.service.resolvePool(self)

    // Return the cached pool, resolving if necessary
    def apply(): Option[NeutrinoPool] = {
      if (pool.isEmpty) pool = resolve()
      pool
    }

    // Set pool by name
    def set(poolname: String): Option[NeutrinoPool] = {
      // If replacing exisiting pool, we need to release any outstanding connections to it's node
      release()

      // Attempt to set a pool with the name provided
      pool = Option(poolname) flatMap (NamedResolver.get(self, _))
      pool
    }

    // Release the endpoint.
    // Note that we don't actually need to return to the right pool; it'll take care of getting
    // it to the right place.
    def release(clearPool: Boolean=true) = {
      require(self.asInstanceOf[NeutrinoRequest].downstream.isEmpty || pool.isDefined, "If downstream is set, must have a valid pool")
      pool map (_.release(self))
      if (clearPool) pool = None
    }
  }
}


/**
 * Static request methods.
 *
 */
object NeutrinoRequest extends StrictLogging {

  import com.ebay.neutrino.util.AttributeSupport._


  def apply(channel: Channel, request: HttpRequest): NeutrinoRequest = {
    // Grab the session out of the channel's attributes
    val session = channel.session.get

    request match {
      case request: FullHttpRequest  => new NeutrinoRequest(session, request) with NeutrinoFullRequest
      case request: LastHttpContent  => new NeutrinoRequest(session, request) with NeutrinoLastHttpContent
      case request: ReferenceCounted => new NeutrinoRequest(session, request) with NeutrinoReferenceCounted
      case request                   => new NeutrinoRequest(session, request)
    }
  }


  /**
   * Create the connection-object and attempt to associate it with a valid pool.
   * @param channel
   * @param httprequest
   */
  def create(channel: Channel, httprequest: HttpRequest): Try[NeutrinoRequest] = {
    // Attempt to create request (catching any URI format exceptions)
    val request = Try(NeutrinoRequest(channel, httprequest))
    val statistics = channel.statistics

    // Track request-session statistics
    request map { request =>
      // Update our metrics
      Metrics.RequestsOpen.inc()
      Metrics.RequestsTotal.mark()

      // Update session metrics
      if (statistics.requestCount == 0) {
        Metrics.SessionActive += 1
        Metrics.SessionTotal.mark
        logger.info("Starting user-session {}", this)
      }

      // Resolve by host (if available) or fallback to the incoming VIP's default pool
      // Register the request-level connection entity on the new channel with the Balancer
      statistics.requestCount.incrementAndGet()
    }

    request
  }


  trait NeutrinoReferenceCounted extends ReferenceCounted { self: NeutrinoRequest =>
    @inline val typed = delegate.asInstanceOf[ReferenceCounted]
    override def refCnt(): Int = typed.refCnt()
    override def retain(): ReferenceCounted = typed.retain()
    override def retain(increment: Int): ReferenceCounted = typed.retain(increment)
    override def touch(): ReferenceCounted = typed.touch()
    override def touch(hint: AnyRef): ReferenceCounted = typed.touch(hint)
    override def release(): Boolean = typed.release()
    override def release(decrement: Int): Boolean = typed.release(decrement)
  }

  trait NeutrinoLastHttpContent extends NeutrinoReferenceCounted with LastHttpContent { self: NeutrinoRequest =>
    @inline override val typed = delegate.asInstanceOf[LastHttpContent]
    override def content(): ByteBuf = typed.content()
    override def copy(): LastHttpContent = typed.copy()
    override def duplicate(): LastHttpContent = typed.duplicate()
    override def retain(): LastHttpContent = typed.retain()
    override def retain(increment: Int): LastHttpContent = typed.retain(increment)
    override def touch(): LastHttpContent = typed.touch()
    override def touch(hint: AnyRef): LastHttpContent = typed.touch(hint)
    override def trailingHeaders(): HttpHeaders = typed.trailingHeaders()
  }

  trait NeutrinoFullRequest extends NeutrinoLastHttpContent with FullHttpRequest { self: NeutrinoRequest =>
    @inline override val typed = delegate.asInstanceOf[FullHttpRequest]
    override def copy(content: ByteBuf): FullHttpRequest = typed.copy(content)
    override def copy(): FullHttpRequest = typed.copy()
    override def retain(increment: Int): FullHttpRequest = typed.retain(increment)
    override def retain(): FullHttpRequest = typed.retain()
    override def touch(): FullHttpRequest = typed.touch()
    override def touch(hint: AnyRef): FullHttpRequest = typed.touch(hint)
    override def duplicate(): FullHttpRequest = typed.duplicate()
    override def setProtocolVersion(version: HttpVersion): FullHttpRequest = typed.setProtocolVersion(version)
    override def setMethod(method: HttpMethod): FullHttpRequest = typed.setMethod(method)
    override def setUri(uri: String): FullHttpRequest = typed.setUri(uri)
  }
}