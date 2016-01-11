package com.ebay.neutrino.handler.ops

import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._


/**
 * Simple diagnostics shim to examine HTTP traffic passing through the channel.
 *
 */
@Sharable
class HttpDiagnosticsHandler extends ChannelDuplexHandler with StrictLogging {
  import com.ebay.neutrino.util.AttributeSupport._

  override def close(ctx: ChannelHandlerContext, future: ChannelPromise): Unit = {
    logger.info("Channel {} Closing.", ctx.session)
    ctx.close(future)
  }

  override def channelRegistered(ctx: ChannelHandlerContext): Unit = {
    logger.info("Channel {} Registered.", ctx.session)
    ctx.fireChannelRegistered()
  }

  override def channelUnregistered(ctx: ChannelHandlerContext): Unit = {
    logger.info("Channel {} Un-registered.", ctx.session)
    ctx.fireChannelUnregistered()
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    logger.info("Channel {} active.", ctx.session)
    ctx.fireChannelActive()
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    logger.info("Channel {} Inactive.", ctx.session)
    ctx.fireChannelInactive()
  }

  override def write(ctx: ChannelHandlerContext, msg: Object, promise: ChannelPromise): Unit = {
    logger.info("Channel {} writing msg {}", ctx.session, msg)
    ctx.write(msg, promise)
  }

  override def flush(ctx: ChannelHandlerContext): Unit = {
    logger.info("Channel {} flushing.", ctx.session)
    ctx.flush()
  }

  override def channelRead(ctx: ChannelHandlerContext, msg: Object): Unit = {
    logger.info("Channel {} reading: {}", ctx.session, msg)
    ctx.fireChannelRead(msg)
  }
}