package com.ebay.neutrino.handler.contrib

import com.ebay.neutrino.NeutrinoRequest
import com.ebay.neutrino.util.HttpRequestUtils
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.buffer.ByteBufHolder
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.handler.codec.http.HttpRequest
import io.netty.util.{Attribute, AttributeKey}

/**
 * Client request/response handler for outbound connections; for use with the Sandbox Pipeline.
 *
 *
 */
@Sharable
class SandboxTop extends ChannelDuplexHandler with StrictLogging {
  import SandboxSupport._

  // Support for deep-object copy; we want to make a copy of any objects coming through which
  // are writeable or not idempotic.
  def clone(msg: AnyRef) = msg match {
    case bytes: ByteBufHolder => bytes.duplicate()
    case request: HttpRequest => HttpRequestUtils.copy(request)
    case msg => msg
  }


  // Register our previous context in the channel-attributes. The outbound contexts are referenced directly.
  override def channelActive(ctx: ChannelHandlerContext) = {
    ctx.outbound = ctx
    ctx.fireChannelActive()
  }

  override def userEventTriggered(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
    ctx.inbound.fireUserEventTriggered(msg)
    ctx.fireUserEventTriggered(msg)
  }

  override def channelRead(ctx: ChannelHandlerContext, msg: Object): Unit = {
    // Split events into two; delegate one down through the bottom and split a secondary through our sandbox
    ctx.inbound.fireChannelRead(msg)
    ctx.fireChannelRead(clone(msg))
  }

  // Swallow outgoing events
  override def write(ctx: ChannelHandlerContext, msg: scala.AnyRef, promise: ChannelPromise): Unit = {}
}


@Sharable
class SandboxBottom extends ChannelDuplexHandler with StrictLogging {
  import SandboxSupport._

  // Register ourselves in the channel-attributes
  override def channelActive(ctx: ChannelHandlerContext) = {
    ctx.inbound = ctx
    ctx.fireChannelActive()
    logger.info("channelActive: Swallowing")
  }

  // Swallow user-messages from within the sandbox
  override def userEventTriggered(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
    logger.info("userEventTriggered: Swallowing")
  }

  // Dipatch sandbox reads out to the secondary sandbox channel
  override def channelRead(ctx: ChannelHandlerContext, msg: Object): Unit = {
    // TODO
    msg match {
      case request: HttpRequest => /*println(s"Connecting T-proxy to ${request.uri()}")*/
/*
        // Clone a balancer-connection
        val sandbox = ctx.connection.get.copy()
        establish(ctx, sandbox)
*/

      case _ =>
    }
  }

  // Circumvent the sandbox and write directly to the outbound
  override def write(ctx: ChannelHandlerContext, msg: scala.AnyRef, promise: ChannelPromise): Unit = {
    ctx.outbound.write(msg, promise)
  }

  def establish(ctx: ChannelHandlerContext, connection: NeutrinoRequest) = {
/*
    core.resolve(connection) map {
      case Some(future) =>
      case None =>
    }*/
  }
}


object SandboxSupport {

  import com.ebay.neutrino.util.AttributeSupport._

  val SandboxOutboundKey = AttributeKey.valueOf[ChannelHandlerContext]("sandbox-top-ctx")
  val SandboxInboundKey  = AttributeKey.valueOf[ChannelHandlerContext]("sandbox-bottom-ctx")


  implicit class SandboxContextSupport(val self: ChannelHandlerContext) extends AnyVal {
    def attr[T](key: AttributeKey[T]): Attribute[T] = self.attr(key)
    def channel = self.channel

    def outbound = self.get(SandboxOutboundKey).get
    def outbound_=(value: ChannelHandlerContext) = self.set(SandboxOutboundKey, Option(value))

    def inbound = self.get(SandboxInboundKey).get
    def inbound_=(value: ChannelHandlerContext) = self.set(SandboxInboundKey, Option(value))
  }
}