package com.ebay.neutrino.handler

import com.codahale.metrics.annotation.{Metered, Timed}
import com.ebay.neutrino.channel.NeutrinoEvent
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._

/**
 * Simulated handler representing a raw Channel Handler.
 *
 * We reimplement only the methods we want to customize.
 */
@Sharable
class ExampleCustomHandler extends ChannelDuplexHandler with StrictLogging {

  /**
   * Example of intercepting write() methods on outbound.
   *
   * Metered annotation: will mark entry of each invocation of a write() and send to absolute metric name
   */
  @Metered(name = "pipeline-requests", absolute = true)
  override def write(ctx: ChannelHandlerContext, msg: AnyRef, promise: ChannelPromise): Unit = {
    ctx.write(msg, promise)
  }


  /**
   * Intercepting user-events (implemented on inbound-handler)
   */
  override def userEventTriggered(ctx: ChannelHandlerContext, evt: AnyRef) = {

    // Some recognized events
    evt match {
      case NeutrinoEvent.RequestCreated(conn) => logger.info("Received start of request event")
      case NeutrinoEvent.ResponseReceived(conn)  => logger.info("Received start of response event")
      case NeutrinoEvent.ResponseCompleted(conn)  => logger.info("Received end of request/response event")
      case _ =>
    }

    ctx.fireUserEventTriggered(evt)
  }


  /**
   * Intercepting read events, on inbound.
   *
   * Timed annotation: will measure elapsed execution time of event and send to (class-based) named metric
   * Metered annotation: apply a secondary meter with the class-relative name provided
   */
  @Timed @Metered(name="metered-reader")
  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
    ctx.fireChannelRead(msg)
  }
}