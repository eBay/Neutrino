package com.ebay.neutrino.handler

import com.ebay.neutrino.channel.NeutrinoPipelineChannel
import com.ebay.neutrino.util.AttributeSupport
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.util.AttributeKey

/**
 * Stateless user-pipeline handler bridge between our parent (system) channel and
 * the user-pipeline, which is intended to be self-containe.
 *
 * On register, this handler will add a user-pipeline to the parent channel context
 * if required (if there are user-handlers). Subsequent events (both inbound and
 * outbound) will be routed through the user-pipeline channel if it's present,
 * and skipped if not.
 *
 *
 * Incomplete/outstanding items:
 * - TODO handle propagation of close from NeutrinoChannel to parent channel
 */
@Sharable
class NeutrinoPipelineHandler extends ChannelDuplexHandler with StrictLogging
{
  import com.ebay.neutrino.handler.NeutrinoPipelineHandler._


  override def channelRegistered(ctx: ChannelHandlerContext) = {
    // Create the custom channel, if we have user handlers
    ctx.userpipeline = NeutrinoPipelineChannel(ctx)

    // Secondly, dispatch the channel-registered to our children
    // (creation would have already registered the internal handlers)
    ctx.fireChannelRegistered()
  }

  override def channelUnregistered(ctx: ChannelHandlerContext) = {
    // If user-pipeline is configured, deregister it and remove
    ctx.userpipeline map { user =>
      user.pipeline.fireChannelUnregistered()
      ctx.userpipeline = None
    }

    ctx.fireChannelUnregistered()
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = ctx.userpipeline match {
    case Some(user) => user.pipeline.fireChannelActive()
    case None => ctx.fireChannelActive()
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = ctx.userpipeline match {
    case Some(user) => user.pipeline.fireChannelInactive()
    case None => ctx.fireChannelInactive()
  }

  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = ctx.userpipeline match {
    case Some(user) => user.pipeline.fireChannelRead(msg)
    case None => ctx.fireChannelRead(msg)
  }

  override def channelWritabilityChanged(ctx: ChannelHandlerContext): Unit = ctx.userpipeline match {
    case Some(user) => user.pipeline.fireChannelWritabilityChanged()
    case None => ctx.fireChannelWritabilityChanged()
  }

  override def channelReadComplete(ctx: ChannelHandlerContext): Unit = ctx.userpipeline match {
    case Some(user) => user.pipeline.fireChannelReadComplete()
    case None => ctx.fireChannelReadComplete()
  }

  override def userEventTriggered(ctx: ChannelHandlerContext, msg: AnyRef): Unit = ctx.userpipeline match {
    case Some(user) => user.pipeline.fireUserEventTriggered(msg)
    case None => ctx.fireUserEventTriggered(msg)
  }

  override def flush(ctx: ChannelHandlerContext): Unit = ctx.userpipeline match {
    case Some(user) => user.pipeline.flush()
    case None => ctx.flush()
  }

  override def close(ctx: ChannelHandlerContext, promise: ChannelPromise): Unit = ctx.userpipeline match {
    case Some(user) => user.close(promise)
    case None => ctx.close(promise)
  }

  //override def read(ctx: ChannelHandlerContext): Unit =
  //  outbound(ctx).read()

  // We'll cheat a little here; we'll assume the downstream handler (shoudl just be 'Downstream')
  // are not going to put a promise on this
  override def write(ctx: ChannelHandlerContext, msg: AnyRef, promise: ChannelPromise) =
    ctx.userpipeline match {
      case Some(user) =>
        logger.info("Writing outbound message to user-pipeline channel: {}", msg)
        //require(promise.isEmpty())
        //user.pipeline.write(msg, promise)
        user.pipeline.write(msg)

      case None =>
        ctx.write(msg, promise)
    }
}


/**
 * Static helpers for User-pipeline support methods, including Attribute getter/setter.
 *
 * These are localized to this file instead of the stock AttributeSupport utilties as we don't
 * need external visibility.
 *
 */
object NeutrinoPipelineHandler {

  import AttributeSupport.AttributeMapSupport

  // Constants
  private val UserPipelineKey = AttributeKey.valueOf[NeutrinoPipelineChannel]("user-pipeline")

  // Neutrino user-pipeline (optional) getter and setter
  implicit private class NeutrinoPipelineSupport(val self: ChannelHandlerContext) extends AnyVal {
    def userpipeline = self.get(UserPipelineKey)
    def userpipeline_=(value: NeutrinoPipelineChannel) = self.set(UserPipelineKey, Option(value))
    def userpipeline_=(value: Option[NeutrinoPipelineChannel]) = self.set(UserPipelineKey, value)
  }
}