package com.ebay.neutrino.handler.ops

import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.handler.codec.http.HttpMessage


/**
 * Simple diagnostics shim to examine HTTP traffic passing through the channel.
 *
 */
@Sharable
class HeaderDiagnosticsHandler(headersOnly: Boolean=true) extends ChannelDuplexHandler with StrictLogging {
  import scala.collection.JavaConversions._


  @inline def format(message: HttpMessage): String =
    message.headers() map { header => s"\t\t${header.getKey} => ${header.getValue}" } mkString("\n")


  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {

    msg match {
      case message: HttpMessage =>
        if (headersOnly)
          logger.info("{} Headers received:\n{}", msg.getClass.getName, format(message))
        else
          logger.info("Message received: {}", msg)

      case _ =>
    }

    ctx.fireChannelRead(msg)
  }


  override def write(ctx: ChannelHandlerContext, msg: AnyRef, promise: ChannelPromise): Unit = {
    msg match {
      case message: HttpMessage =>
        if (headersOnly)
          logger.info("{} Headers sent:\n{}", msg.getClass.getName, format(message))
        else
          logger.info("Message sent: {}", msg)

      case _ =>
    }

    ctx.write(msg, promise)
  }
}