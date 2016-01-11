package com.ebay.neutrino.channel

import java.net.SocketAddress

import com.ebay.neutrino.channel.NeutrinoPipelineChannel.ChannelState
import com.ebay.neutrino.metrics.Instrumented
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.channel._
import io.netty.util.{Attribute, AttributeKey, ReferenceCountUtil}

/**
 * Base class for {@link Channel} NeutrinoChannel implementations; built on the EmbeddedChannel
 * framework.
 *
 * NeutrinoChannel (EmbeddedChannel) is:
 * - container for pipeline
 * - dedicated to protocol (ie: HTTP)
 * - tightly coupled with the "request" (not connection).
 * - Knows about each endpoint's framing protocol
 * - is responsible for framing down into the endpoints' frames
 *
 * For simplicity, writing into the EmbeddedChannel handles memory lifecycle and manages virtual
 * session containing...
 */
// TODO support user-pipeline thread
class NeutrinoPipelineChannel(parentctx: ChannelHandlerContext)
  extends AbstractChannel(parentctx.channel, NeutrinoChannelId())
  with StrictLogging
  with Instrumented
{
  import ChannelFutureListener._


  class ForwardingHandler extends ChannelHandlerAdapter with ChannelInboundHandler {

    override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) =
      parentctx.fireExceptionCaught(cause)

    override def channelRegistered(ctx: ChannelHandlerContext): Unit =
      parentctx.fireChannelRegistered()

    override def channelUnregistered(ctx: ChannelHandlerContext): Unit =
      parentctx.fireChannelUnregistered()

    override def channelActive(ctx: ChannelHandlerContext): Unit =
      parentctx.fireChannelActive()

    override def channelInactive(ctx: ChannelHandlerContext): Unit =
      parentctx.fireChannelInactive()

    override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit =
      parentctx.fireChannelRead(msg)

    override def channelWritabilityChanged(ctx: ChannelHandlerContext): Unit =
      parentctx.fireChannelWritabilityChanged()

    override def userEventTriggered(ctx: ChannelHandlerContext, evt: AnyRef): Unit =
      parentctx.fireUserEventTriggered(evt)

    override def channelReadComplete(ctx: ChannelHandlerContext): Unit =
      parentctx.fireChannelReadComplete()
  }

  private class DefaultUnsafe extends AbstractUnsafe {
    override def connect(remote: SocketAddress, local: SocketAddress, promise: ChannelPromise): Unit =
      safeSetSuccess(promise)

    //override protected def flush0() = super.flush0()
  }

  // Immutable data
  val config = new DefaultChannelConfig(this)

  // Mutable data
  private var parentPromise: ChannelPromise = null
  @transient private var state: ChannelState = ChannelState.Open



  /**
   * Register the provided event-loop and, after registration, mark ourselves as active.
   */
  private[channel] def register() = {
    // Register ourselves to our event-loop (forcing notification of the channel)
    require(state == ChannelState.Open)
    parent.eventLoop.register(this)
    require(state == ChannelState.Registered)

    // 'Activate' our channel
    state = ChannelState.Active
  }

  override protected def newUnsafe: AbstractChannel#AbstractUnsafe =
    new DefaultUnsafe

  override protected def isCompatible(loop: EventLoop): Boolean =
    loop.isInstanceOf[SingleThreadEventLoop]

  override protected def doBind(localAddress: SocketAddress) =
    {}

  override protected def doDisconnect =
    {}

  override protected def doClose = {
    state = ChannelState.Closed

    parentPromise match {
      case null =>
        parentctx.channel.close()
      case valid =>
        parentctx.close(parentPromise)
        parentPromise = null
    }
  }

  override def metadata: ChannelMetadata = NeutrinoPipelineChannel.METADATA

  override protected def doBeginRead = parentctx.read()    //if (isOpen) (this.upstream getOrElse parent).read()

  override protected def doRegister() = (state = ChannelState.Registered)

  // Channel is open if we haven't been closed and our parent channel is open
  override def isOpen: Boolean = (state != ChannelState.Closed && parentctx.channel.isOpen)

  // Channel is active if we haven't been closed and our parent channel is active
  override def isActive: Boolean = (state == ChannelState.Active && parentctx.channel.isActive)



  /**
   * Get the {@link Attribute} for the given {@link AttributeKey}.
   * This method will never return null, but may return an {@link Attribute} which does not have a value set yet.
   */
  override def attr[T](key: AttributeKey[T]): Attribute[T] = parent.attr(key)

  /**
   * Returns {@code} true if and only if the given {@link Attribute} exists in this {@link AttributeMap}.
   */
  override def hasAttr[T](key: AttributeKey[T]): Boolean = parent.hasAttr(key)

  /**
   * Override the embedded channel's local address to map to the underlying channel.
   * @return
   */
  override protected def localAddress0(): SocketAddress = parent.localAddress()

  /**
   * Override the embedded channel's remote address to map to the underlying channel.
   * @return
   */
  override protected def remoteAddress0(): SocketAddress = parent.remoteAddress()


  /**
   * This is called by the AbstractChannel after write/flush through the pipeline
   * is completed.
   *
   * Instead of using outbound message paths, we wire directly back to the upstream
   * context (which should delegate to the next handler in the upstream-parent.
   *
   * @param in
   *
   *
   * NOTE - we currently do this functionality in the UpstreamHandler instead of here.
   */
  override protected def doWrite(in: ChannelOutboundBuffer): Unit = {
    val outbound = Iterator.continually(in.current()) takeWhile (_ != null)

    outbound foreach { msg =>
      ReferenceCountUtil.retain(msg)

      // TODO handle entry promise; hook promise on future

      // Do the write and attach an appropriate future to it
      val listener = if (isOpen) CLOSE_ON_FAILURE else CLOSE
      val future   = parentctx.write(msg).addListener(listener)

      // Signal removal from the current channel.
      in.remove()

      // Flush as required
      if (in.isEmpty) parentctx.flush()
    }
  }


  /**
   * Hack a custom promise to carry our parent framework's promise through and dispatch the close
   * properly back, allowing differential behaviour between close() invoked internally vs externally.
   *
   * @param parent
   * @return
   */
  override def close(parent: ChannelPromise): ChannelFuture =
    if (parent.channel() == this) {
      super.close(parent)
    }
    else {
      parentPromise = parent
      super.close()
    }

}


object NeutrinoPipelineChannel {
  import com.ebay.neutrino.util.AttributeSupport._

  // Constants
  private val METADATA = new ChannelMetadata(false)

  /**
   * Channel state constants; these track the allowable states.
   */
  sealed private[channel] trait ChannelState

  private[channel] object ChannelState {
    case object Closed extends ChannelState
    case object Open   extends ChannelState
    case object Active extends ChannelState
    case object Registered extends ChannelState
  }

  /**
   * Create a new pipeline-channel and do the initial channel setup.
   *
   */
  def apply(parentctx: ChannelHandlerContext, handlers: Seq[ChannelHandler]): NeutrinoPipelineChannel =
  {
    // Create the custom channel
    val channel = new NeutrinoPipelineChannel(parentctx)

    // Add the handlers.
    channel.pipeline.addFirst(handlers:_*)

    // Register ourselves to our event-loop (forcing notification of the channel)
    channel.register()

    // Finally, add a relaying handler to dispatch out from the bottom of the pipeline
    channel.pipeline.addLast("user-loopback", new channel.ForwardingHandler)

    // Hook the parents' close-future for force closing of our new channel
    parentctx.channel.closeFuture.addListener(new ChannelFutureListener {
      override def operationComplete(future: ChannelFuture): Unit =
        if (channel.isOpen) channel.close()
    })

    channel
  }


  def apply(parentctx: ChannelHandlerContext): Option[NeutrinoPipelineChannel] = {
    // Extract handler settings and determine if pipeline is required
    val settings = parentctx.service map (_.settings)
    val handlers = settings map (_.handlers) filter (_.nonEmpty)

    // If configured, create pipeline channel around it
    handlers map { apply(parentctx, _) }
  }
}