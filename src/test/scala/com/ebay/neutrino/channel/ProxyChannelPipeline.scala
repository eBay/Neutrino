package com.ebay.neutrino.channel

import java.net.SocketAddress
import java.util
import java.util.Map.Entry

import io.netty.channel._
import io.netty.util.concurrent.EventExecutorGroup


class ProxyChannelPipeline(delegate: ChannelPipeline) extends Proxy with ChannelPipeline {
  override def self = delegate

  override def toMap: util.Map[String, ChannelHandler] =
    delegate.toMap

  override def names(): util.List[String] =
    delegate.names()

  override def channel(): Channel =
    delegate.channel()

  override def first(): ChannelHandler =
    delegate.first()

  override def last(): ChannelHandler =
    delegate.last()

  override def get(name: String): ChannelHandler =
    delegate.get(name)

  override def get[T <: ChannelHandler](handlerType: Class[T]): T =
    delegate.get(handlerType)

  override def iterator(): util.Iterator[Entry[String, ChannelHandler]] =
    delegate.iterator()

  override def context(handler: ChannelHandler): ChannelHandlerContext =
    delegate.context(handler)

  override def context(name: String): ChannelHandlerContext =
    delegate.context(name)

  override def context(handlerType: Class[_ <: ChannelHandler]): ChannelHandlerContext =
    delegate.context(handlerType)

  override def firstContext(): ChannelHandlerContext =
    delegate.firstContext()

  override def lastContext(): ChannelHandlerContext =
    delegate.lastContext()


  override def bind(localAddress: SocketAddress): ChannelFuture =
    delegate.bind(localAddress)

  override def bind(localAddress: SocketAddress, promise: ChannelPromise): ChannelFuture =
    delegate.bind(localAddress, promise)

  override def connect(remoteAddress: SocketAddress): ChannelFuture =
    delegate.connect(remoteAddress)

  override def connect(remoteAddress: SocketAddress, localAddress: SocketAddress): ChannelFuture =
    delegate.connect(remoteAddress, localAddress)

  override def connect(remoteAddress: SocketAddress, promise: ChannelPromise): ChannelFuture =
    delegate.connect(remoteAddress, promise)

  override def connect(remoteAddress: SocketAddress, localAddress: SocketAddress, promise: ChannelPromise): ChannelFuture =
    delegate.connect(remoteAddress, localAddress, promise)

  override def disconnect(): ChannelFuture =
    delegate.disconnect()

  override def disconnect(promise: ChannelPromise): ChannelFuture =
    delegate.disconnect(promise)


  override def read(): ChannelPipeline =
    delegate.read()

  override def write(msg: scala.Any): ChannelFuture =
    delegate.write(msg)

  override def write(msg: scala.Any, promise: ChannelPromise): ChannelFuture =
    delegate.write(msg, promise)

  override def writeAndFlush(msg: scala.Any, promise: ChannelPromise): ChannelFuture =
    delegate.writeAndFlush(msg, promise)

  override def writeAndFlush(msg: scala.Any): ChannelFuture =
    delegate.writeAndFlush(msg)

  override def flush(): ChannelPipeline =
    delegate.flush()

  override def close(): ChannelFuture =
    delegate.close()

  override def close(promise: ChannelPromise): ChannelFuture =
    delegate.close(promise)

  override def deregister(): ChannelFuture =
    delegate.deregister()

  override def deregister(promise: ChannelPromise): ChannelFuture =
    delegate.deregister(promise)


  override def fireChannelRegistered(): ChannelPipeline =
    delegate.fireChannelRegistered()

  override def fireChannelUnregistered(): ChannelPipeline =
    delegate.fireChannelUnregistered()

  override def fireChannelActive(): ChannelPipeline =
    delegate.fireChannelActive()

  override def fireChannelInactive(): ChannelPipeline =
    delegate.fireChannelInactive()

  override def fireChannelRead(msg: scala.Any): ChannelPipeline =
    delegate.fireChannelRead(msg)

  override def fireChannelReadComplete(): ChannelPipeline =
    delegate.fireChannelReadComplete()

  override def fireChannelWritabilityChanged(): ChannelPipeline =
    delegate.fireChannelWritabilityChanged()

  override def fireUserEventTriggered(event: scala.Any): ChannelPipeline =
    delegate.fireUserEventTriggered(event)

  override def fireExceptionCaught(cause: Throwable): ChannelPipeline =
    delegate.fireExceptionCaught(cause)


  override def addFirst(name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addFirst(name, handler)

  override def addFirst(group: EventExecutorGroup, name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addFirst(group, name, handler)

  override def addFirst(invoker: ChannelHandlerInvoker, name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addFirst(invoker, name, handler)

  override def addFirst(handlers: ChannelHandler*): ChannelPipeline =
    delegate.addFirst(handlers:_*)

  override def addFirst(group: EventExecutorGroup, handlers: ChannelHandler*): ChannelPipeline =
    delegate.addFirst(group, handlers:_*)

  override def addFirst(invoker: ChannelHandlerInvoker, handlers: ChannelHandler*): ChannelPipeline =
    delegate.addFirst(invoker, handlers:_*)

  override def addBefore(baseName: String, name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addBefore(baseName, name, handler)

  override def addBefore(group: EventExecutorGroup, baseName: String, name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addBefore(group, baseName, name, handler)

  override def addBefore(invoker: ChannelHandlerInvoker, baseName: String, name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addBefore(invoker, baseName, name, handler)

  override def addAfter(baseName: String, name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addAfter(baseName, name, handler)

  override def addAfter(group: EventExecutorGroup, baseName: String, name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addAfter(group, baseName, name, handler)

  override def addAfter(invoker: ChannelHandlerInvoker, baseName: String, name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addAfter(invoker, baseName, name, handler)

  override def addLast(name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addLast(name, handler)

  override def addLast(group: EventExecutorGroup, name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addLast(group, name, handler)

  override def addLast(invoker: ChannelHandlerInvoker, name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addLast(invoker, name, handler)

  override def addLast(handlers: ChannelHandler*): ChannelPipeline =
    delegate.addLast(handlers:_*)

  override def addLast(group: EventExecutorGroup, handlers: ChannelHandler*): ChannelPipeline =
    delegate.addLast(group, handlers:_*)

  override def addLast(invoker: ChannelHandlerInvoker, handlers: ChannelHandler*): ChannelPipeline =
    delegate.addLast(invoker, handlers:_*)

  override def replace(oldHandler: ChannelHandler, newName: String, newHandler: ChannelHandler): ChannelPipeline =
    delegate.replace(oldHandler, newName, newHandler)

  override def replace(oldName: String, newName: String, newHandler: ChannelHandler): ChannelHandler =
    delegate.replace(oldName, newName, newHandler)

  override def replace[T <: ChannelHandler](oldHandlerType: Class[T], newName: String, newHandler: ChannelHandler): T =
    delegate.replace(oldHandlerType, newName, newHandler)

  override def remove(handler: ChannelHandler): ChannelPipeline =
    delegate.remove(handler)

  override def remove(name: String): ChannelHandler =
    delegate.remove(name)

  override def remove[T <: ChannelHandler](handlerType: Class[T]): T =
    delegate.remove[T](handlerType)

  override def removeFirst(): ChannelHandler =
    delegate.removeFirst()

  override def removeLast(): ChannelHandler =
    delegate.removeLast()
}