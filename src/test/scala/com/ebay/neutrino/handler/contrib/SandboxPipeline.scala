package com.ebay.neutrino.handler.contrib

import com.ebay.neutrino.channel.NeutrinoPipeline
import com.ebay.neutrino.handler._
import com.ebay.neutrino.handler.pipeline.{NeutrinoHandler, DelegateOutboundAdapter, DelegateInboundAdapter, DelegateHandler}
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._


/**
 * Helper object for the sandbox-pipeline; this message is intended to mark messages that
 * should not be dispatched by sandbox handlers
 * @param msg
 */
case class SandboxMessage(msg: AnyRef)


/**
 * Implementation of Splitter-functionality.
 *
 * SplitterHandler provides a top/bottom sandwich for routing around 'user' pipline handlers that
 * we want to sandbox.
 *
 * Handler-methods we want to hook:
 * - Inbound:  channelRead()
 * - Inbound:  userEventTriggered()
 * - Outbound: write()
 *
 * These methods can't be supported (they don't have a message we can wrap, or the
 * message isn't generic enough); they just pass-through and we rely on the user-pipeline
 * forwarding them properly:
 * - Inbound:  exceptionCaught
 * - read()
 * - flush()
 * - close()
 *
 *
 */
@Sharable
class SandboxBarrier(top: Boolean) extends ChannelDuplexHandler with StrictLogging
{
  def bottom = !top


  def filter(inbound: Boolean, message: AnyRef)(f: AnyRef => Unit) = {
    val front = if (inbound) top else bottom
    val back  = if (inbound) bottom else top

    message match {
      // Split message into pass-thru and sandbox
      case msg if front =>
        f(SandboxMessage(msg))
        f(msg)

      // Unroll pass-thru events
      case SandboxMessage(msg) =>
        require(back, s"SandboxMessage should not come from side of Sandbox: Inbound=$inbound, Top=$top")
        f(msg)

      // Swallow sandboxed (empty) message
      case msg if back =>
    }
  }


  // Inbound events
  //
  override def userEventTriggered(ctx: ChannelHandlerContext, msg: AnyRef): Unit =
    filter(true, msg) { ctx.fireUserEventTriggered(_) }


  override def channelRead(ctx: ChannelHandlerContext, msg: Object): Unit =
    filter(true, msg) { ctx.fireChannelRead(_) }


  // Outbound events
  override def write(ctx: ChannelHandlerContext, msg: scala.AnyRef, promise: ChannelPromise): Unit =
    filter(false, msg) { ctx.write(_) }
}


/**
 * A delegate implementation of the ChannelPipeline to assist with Neutrino pipeline
 * creation.
 *
 * Subclasses are expected to extend/reimplement initPipline(), and to delegate up to
 * us on completion of initialization.
 */
class SandboxPipelineInitializer {

  /**
   * Create a new Neutrino pipeline based on the channel pipeline provided
   */
  def create(pipeline: ChannelPipeline): NeutrinoPipeline = new SandboxPipeline(pipeline)
}


class SandboxPipeline(delegate: ChannelPipeline) extends NeutrinoPipeline(delegate) {

  class Inbound[T <: ChannelInboundHandler](inbound: T)
    extends DelegateHandler[T](inbound) with NeutrinoHandler with SandboxInboundHandler[T]

  class Outbound[T <: ChannelOutboundHandler](outbound: T)
    extends DelegateHandler[T](outbound) with NeutrinoHandler with SandboxOutboundHandler[T]

  class Duplex[T <: ChannelInboundHandler with ChannelOutboundHandler](both: T)
    extends DelegateHandler[T](both) with NeutrinoHandler with SandboxInboundHandler[T] with SandboxOutboundHandler[T]


  val top = new SandboxTop()
  val bottom = new SandboxBottom()


  /**
   * Create a new operational ChannelHandler adapter to provide operational delegation.
   *
   * @param handler
   * @return
   *
  override def toNeutrino(handler: ChannelHandler): NeutrinoHandler = {
    // Re-wrap the neutrino handler in our own sandbox delegate
    super.toNeutrino(handler) match {
      case both: ChannelInboundHandler with ChannelOutboundHandler => new Duplex(both)
      case inbound:  ChannelInboundHandler  => new Inbound(inbound)
      case outbound: ChannelOutboundHandler => new Outbound(outbound)
      case other => other
    }
  }
   */

  /**
   * Initialize the sandbox-pipeline by wrapping the user-pipeline with our custom
   * sandbox handlers.
   */
  def initialize(pipeline: ChannelPipeline): Unit = {
    // Box our existing pipeline with the sandbox handler sandwich
    delegate.addFirst("sandbox-top", top)
    delegate.addLast("sandbox-bottom", bottom)
  }
}


/**
 * Inbound and outbound sandbox handlers; filter out any sandbox messages and only delegate through
 * 'valid' messages.
 */
trait SandboxInboundHandler[T <: ChannelInboundHandler] extends DelegateInboundAdapter[T] {

  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = msg match {
    case SandboxMessage(_) => ctx.fireChannelRead(msg)
    case _ => delegate.channelRead(ctx, msg)
  }

  override def userEventTriggered(ctx: ChannelHandlerContext, msg: AnyRef): Unit = msg match {
    case SandboxMessage(_) => ctx.fireUserEventTriggered(msg)
    case _ => delegate.userEventTriggered(ctx, msg)
  }
}

trait SandboxOutboundHandler[T <: ChannelOutboundHandler] extends DelegateOutboundAdapter[T] {

  override def write(ctx: ChannelHandlerContext, msg: scala.Any, promise: ChannelPromise): Unit = msg match {
    case SandboxMessage(_) => ctx.write(msg, promise)
    case _ => delegate.write(ctx, msg, promise)
  }
}