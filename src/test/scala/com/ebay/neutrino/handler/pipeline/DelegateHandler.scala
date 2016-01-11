package com.ebay.neutrino.handler.pipeline

import java.net.SocketAddress

import io.netty.channel._


/**
 * Default proxy implementations of our common ChannelHandler types.
 * Intended for use with overriding default Netty channel internals implementation.
 *
 * @param delegate
 */
class DelegateHandler[T <: ChannelHandler](val delegate: T) extends ChannelHandler with Proxy
{
  override def self: T = delegate

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit =
    delegate.exceptionCaught(ctx, cause)

  override def handlerRemoved(ctx: ChannelHandlerContext): Unit =
    delegate.handlerRemoved(ctx)

  override def handlerAdded(ctx: ChannelHandlerContext): Unit =
    delegate.handlerAdded(ctx)
}


trait DelegateInboundAdapter[T <: ChannelInboundHandler] extends DelegateHandler[T] with ChannelInboundHandler
{
  def delegate: T


  override def channelRegistered(ctx: ChannelHandlerContext): Unit =
    delegate.channelRegistered(ctx)

  override def channelUnregistered(ctx: ChannelHandlerContext): Unit =
    delegate.channelUnregistered(ctx)

  override def channelActive(ctx: ChannelHandlerContext): Unit =
    delegate.channelActive(ctx)

  override def channelInactive(ctx: ChannelHandlerContext): Unit =
    delegate.channelInactive(ctx)

  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit =
    delegate.channelRead(ctx, msg)

  override def channelWritabilityChanged(ctx: ChannelHandlerContext): Unit =
    delegate.channelWritabilityChanged(ctx)

  override def userEventTriggered(ctx: ChannelHandlerContext, evt: AnyRef): Unit =
    delegate.userEventTriggered(ctx, evt)

  override def channelReadComplete(ctx: ChannelHandlerContext): Unit =
    delegate.channelReadComplete(ctx)
}


trait DelegateOutboundAdapter[T <: ChannelOutboundHandler] extends DelegateHandler[T] with ChannelOutboundHandler
{
  def delegate: T


  override def bind(ctx: ChannelHandlerContext, localAddress: SocketAddress, promise: ChannelPromise): Unit =
    delegate.bind(ctx, localAddress, promise)

  override def disconnect(ctx: ChannelHandlerContext, promise: ChannelPromise): Unit =
    delegate.disconnect(ctx, promise)

  override def flush(ctx: ChannelHandlerContext): Unit =
    delegate.flush(ctx)

  override def write(ctx: ChannelHandlerContext, msg: scala.Any, promise: ChannelPromise): Unit =
    delegate.write(ctx, msg, promise)

  override def close(ctx: ChannelHandlerContext, promise: ChannelPromise): Unit =
    delegate.close(ctx, promise)

  override def read(ctx: ChannelHandlerContext): Unit =
    delegate.read(ctx)

  override def deregister(ctx: ChannelHandlerContext, promise: ChannelPromise): Unit =
    delegate.deregister(ctx, promise)

  override def connect(ctx: ChannelHandlerContext, remoteAddress: SocketAddress, localAddress: SocketAddress, promise: ChannelPromise): Unit =
    delegate.connect(ctx, remoteAddress, localAddress, promise)
}