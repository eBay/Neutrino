package com.ebay.neutrino.channel

import java.net.SocketAddress
import java.util
import java.util.concurrent.TimeUnit

import io.netty.channel.ChannelHandlerInvokerUtil._
import io.netty.channel._
import io.netty.util.concurrent.{EventExecutor, Future => NettyFuture}



/**
 * This was stolen directly from EmbeddedEventLoop, which unfortunately was package scoped and
 * couldn't be used directly.
 *
 * @see io.netty.channel.embedded.EmbeddedEventLoop
 */
final class NeutrinoChannelLoop extends AbstractEventLoop with ChannelHandlerInvoker {
  private final val tasks = new util.ArrayDeque[Runnable](2)

  def execute(command: Runnable) {
    if (command == null) {
      throw new NullPointerException("command")
    }
    tasks.add(command)
  }


  // ?? Can we cache the iterator and call takeWhile on each iteration?
  private[neutrino] def runTasks() =
    Iterator.continually(tasks.poll) takeWhile(_ != null) foreach (_.run)


  def shutdownGracefully(quietPeriod: Long, timeout: Long, unit: TimeUnit): NettyFuture[_] =
    throw new UnsupportedOperationException

  def terminationFuture: NettyFuture[_] =
    throw new UnsupportedOperationException

  @Deprecated
  def shutdown = throw new UnsupportedOperationException

  def isShuttingDown: Boolean = false

  def isShutdown: Boolean = false

  def isTerminated: Boolean = false

  def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = false

  def register(channel: Channel): ChannelFuture =
    register(channel, new DefaultChannelPromise(channel, this))


  def register(channel: Channel, promise: ChannelPromise): ChannelFuture = {
    channel.unsafe.register(this, promise)
    promise
  }

  override def inEventLoop: Boolean = true

  def inEventLoop(thread: Thread): Boolean = true

  def asInvoker: ChannelHandlerInvoker = this

  def executor: EventExecutor = this

  def invokeChannelRegistered(ctx: ChannelHandlerContext) =
    invokeChannelRegisteredNow(ctx)

  def invokeChannelUnregistered(ctx: ChannelHandlerContext) =
    invokeChannelUnregisteredNow(ctx)

  def invokeChannelActive(ctx: ChannelHandlerContext) =
    invokeChannelActiveNow(ctx)

  def invokeChannelInactive(ctx: ChannelHandlerContext) =
    invokeChannelInactiveNow(ctx)

  def invokeExceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) =
    invokeExceptionCaughtNow(ctx, cause)

  def invokeUserEventTriggered(ctx: ChannelHandlerContext, event: AnyRef) =
    invokeUserEventTriggeredNow(ctx, event)

  def invokeChannelRead(ctx: ChannelHandlerContext, msg: AnyRef) =
    invokeChannelReadNow(ctx, msg)

  def invokeChannelReadComplete(ctx: ChannelHandlerContext) =
    invokeChannelReadCompleteNow(ctx)

  def invokeChannelWritabilityChanged(ctx: ChannelHandlerContext) =
    invokeChannelWritabilityChangedNow(ctx)

  def invokeBind(ctx: ChannelHandlerContext, localAddress: SocketAddress, promise: ChannelPromise) =
    invokeBindNow(ctx, localAddress, promise)

  def invokeConnect(ctx: ChannelHandlerContext, remoteAddress: SocketAddress, localAddress: SocketAddress, promise: ChannelPromise) =
    invokeConnectNow(ctx, remoteAddress, localAddress, promise)

  def invokeDisconnect(ctx: ChannelHandlerContext, promise: ChannelPromise) =
    invokeDisconnectNow(ctx, promise)

  def invokeClose(ctx: ChannelHandlerContext, promise: ChannelPromise) =
    invokeCloseNow(ctx, promise)

  def invokeDeregister(ctx: ChannelHandlerContext, promise: ChannelPromise) =
    invokeDeregisterNow(ctx, promise)

  def invokeRead(ctx: ChannelHandlerContext) =
    invokeReadNow(ctx)

  def invokeWrite(ctx: ChannelHandlerContext, msg: AnyRef, promise: ChannelPromise) =
    invokeWriteNow(ctx, msg, promise)

  def invokeFlush(ctx: ChannelHandlerContext) =
    invokeFlushNow(ctx)
}