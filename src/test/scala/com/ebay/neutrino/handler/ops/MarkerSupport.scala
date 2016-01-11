package com.ebay.neutrino.handler.ops

import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}


// Not currently needed - pipeline functionality only used in /test
// Please restore if moved back to /main
class InboundMarker extends ChannelInboundHandlerAdapter with Marker {

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit =
    mark("exceptionCaught") { ctx.fireExceptionCaught(cause) }

  override def channelActive(ctx: ChannelHandlerContext): Unit =
    mark("channelActive") { ctx.fireChannelActive() }

  override def channelInactive(ctx: ChannelHandlerContext): Unit =
    mark("channelInactive") { ctx.fireChannelInactive() }

  override def channelUnregistered(ctx: ChannelHandlerContext): Unit =
    mark("channelUnregistered") { ctx.fireChannelUnregistered() }

  override def channelRead(ctx: ChannelHandlerContext, msg: scala.Any): Unit =
    mark("channelRead") { ctx.fireChannelRead(msg) }

  override def channelRegistered(ctx: ChannelHandlerContext): Unit =
    mark("channelRegistered") { ctx.fireChannelRegistered() }

  override def channelReadComplete(ctx: ChannelHandlerContext): Unit =
    mark("channelReadComplete") { ctx.fireChannelReadComplete() }

  override def userEventTriggered(ctx: ChannelHandlerContext, evt: scala.Any): Unit =
    mark("userEventTriggered") { ctx.fireUserEventTriggered(evt) }

  override def channelWritabilityChanged(ctx: ChannelHandlerContext): Unit =
    mark("channelWritabilityChanged") { ctx.fireChannelWritabilityChanged() }
}


trait Marker {
  import scala.collection.mutable

  // Data-structure for counting our method invocations
  var markerCounts: mutable.Map[String, Int] = mutable.Map()

  // Clear mark
  def clearMark() = markerCounts = mutable.Map.empty

  def isEmpty() = markerCounts.isEmpty

  // Increment mark
  def mark[T](marker: String)(f: => T ={}) = {
    markerCounts += marker -> (markerCounts.getOrElse(marker, 0)+1)
    f
  }
}