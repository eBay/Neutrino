package com.ebay.neutrino.channel

import com.ebay.neutrino.handler._
import com.ebay.neutrino.handler.pipeline.{NeutrinoHandler, NeutrinoProxyHandler}
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.channel._
import io.netty.util.concurrent.EventExecutorGroup


/**
 * A delegate implementation of the ChannelPipeline to assist with Neutrino pipeline
 * creation.
 *
 *
 */
class NeutrinoPipeline(delegate: ChannelPipeline) extends ProxyChannelPipeline(delegate) with StrictLogging
{
  /**
   * Ensure the handler provided is a Neutrino handler, and wrap if not
   */
  def toNeutrino(handler: ChannelHandler): NeutrinoHandler = {
    logger.info("Registering Neutrino handler for {}", handler.getClass.toString)

    handler match {
      case h: NeutrinoHandler => h
      case h => NeutrinoProxyHandler(h)
    }
  }


  override def addFirst(name: String, handler: ChannelHandler): ChannelPipeline =
    super.addFirst(name, toNeutrino(handler))

  override def addFirst(group: EventExecutorGroup, name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addFirst(group, name, toNeutrino(handler))

  override def addFirst(invoker: ChannelHandlerInvoker, name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addFirst(invoker, name, toNeutrino(handler))

  override def addFirst(handlers: ChannelHandler*): ChannelPipeline =
    delegate.addFirst(handlers map (toNeutrino(_)):_*)

  override def addFirst(group: EventExecutorGroup, handlers: ChannelHandler*): ChannelPipeline =
    delegate.addFirst(group, handlers map (toNeutrino(_)):_*)

  override def addFirst(invoker: ChannelHandlerInvoker, handlers: ChannelHandler*): ChannelPipeline =
    delegate.addFirst(invoker, handlers map (toNeutrino(_)):_*)

  override def addBefore(baseName: String, name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addBefore(baseName, name, toNeutrino(handler))

  override def addBefore(group: EventExecutorGroup, baseName: String, name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addBefore(group, baseName, name, toNeutrino(handler))

  override def addBefore(invoker: ChannelHandlerInvoker, baseName: String, name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addBefore(invoker, baseName, name, toNeutrino(handler))

  override def addAfter(baseName: String, name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addAfter(baseName, name, toNeutrino(handler))

  override def addAfter(group: EventExecutorGroup, baseName: String, name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addAfter(group, baseName, name, toNeutrino(handler))

  override def addAfter(invoker: ChannelHandlerInvoker, baseName: String, name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addAfter(invoker, baseName, name, toNeutrino(handler))

  override def addLast(name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addLast(name, toNeutrino(handler))

  override def addLast(group: EventExecutorGroup, name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addLast(group, name, toNeutrino(handler))

  override def addLast(invoker: ChannelHandlerInvoker, name: String, handler: ChannelHandler): ChannelPipeline =
    delegate.addLast(invoker, name, toNeutrino(handler))

  override def addLast(handlers: ChannelHandler*): ChannelPipeline =
    delegate.addLast(handlers map (toNeutrino(_)):_*)

  override def addLast(group: EventExecutorGroup, handlers: ChannelHandler*): ChannelPipeline =
    delegate.addLast(group, handlers map (toNeutrino(_)):_*)

  override def addLast(invoker: ChannelHandlerInvoker, handlers: ChannelHandler*): ChannelPipeline =
    delegate.addLast(invoker, handlers map (toNeutrino(_)):_*)

  override def replace(oldHandler: ChannelHandler, newName: String, newHandler: ChannelHandler): ChannelPipeline =
    delegate.replace(oldHandler, newName, toNeutrino(newHandler))

  override def replace(oldName: String, newName: String, newHandler: ChannelHandler): ChannelHandler =
    delegate.replace(oldName, newName, toNeutrino(newHandler))

  override def replace[T <: ChannelHandler](oldHandlerType: Class[T], newName: String, newHandler: ChannelHandler): T =
    delegate.replace(oldHandlerType, newName, toNeutrino(newHandler))

}