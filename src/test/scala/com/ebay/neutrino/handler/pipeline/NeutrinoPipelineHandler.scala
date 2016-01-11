package com.ebay.neutrino.handler.pipeline

import com.codahale.metrics.annotation.Timed
import com.ebay.neutrino.NeutrinoRequest
import com.ebay.neutrino.handler.pipeline.HttpInboundPipeline.Status
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.channel._
import io.netty.handler.codec.http.{HttpRequest, HttpResponse, HttpResponseStatus}


abstract class NeutrinoInboundPipelineHandler
  extends ChannelInboundHandlerAdapter
  with NeutrinoInboundPipelineTrait

abstract class NeutrinoOutboundPipelineHandler
  extends ChannelOutboundHandlerAdapter
  with NeutrinoOutboundPipelineTrait

abstract class NeutrinoPipelineHandler
  extends ChannelDuplexHandler
  with NeutrinoInboundPipelineTrait
  with NeutrinoOutboundPipelineTrait


/**
 * Support types for request processing.
 */
object NeutrinoInbound {

  sealed trait RequestStatus
  case class Continue(request: Option[HttpRequest]=None) extends RequestStatus
  case class Reject(response: Option[HttpResponse]=None, status: HttpResponseStatus=HttpResponseStatus.FORBIDDEN) extends RequestStatus
}


/**
 * Neutrino Inbound/Outbound Handler Base-Class.
 *
 * Provides message support for the following generic use-cases:
 * a) Neutrino-lifecycle events
 * b) Connection-level pipeline events
 * c) Connection-level
 *
 * TODO handle @RequestSharable handlers...?
 */

sealed trait NeutrinoPipelineTrait extends StrictLogging {

  // Overrideable settings
  def connectionRequired(): Boolean = true

}

sealed trait NeutrinoInboundPipelineTrait extends ChannelInboundHandler with NeutrinoPipelineTrait with HttpInboundPipeline
{
  import com.ebay.neutrino.util.AttributeSupport._


  // TODO handle addListener(ChannelFutureListener.CLOSE) on error?? Always? Some errors?
  @Timed
  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
    require (connectionRequired || ctx.session.isDefined, "Session is required for this channel and has not been initialized")

    msg match {
      // Valid connection
      case request: NeutrinoRequest =>
        // Run the user pipeline
        val result = execute(request)

        result.status match {
          case Status.SKIPPED | Status.CONTINUE =>
            ctx.fireChannelRead(request)

          case Status.COMPLETE | Status.REJECT =>
            ctx.writeAndFlush(result.response)
        }

      // Fall through
      case _ =>
        ctx.fireChannelRead(msg)
    }
  }
}

sealed trait NeutrinoOutboundPipelineTrait extends ChannelOutboundHandler with NeutrinoPipelineTrait
{
  import com.ebay.neutrino.util.AttributeSupport._


  /**
   * Enable this to intercept outbound pipeline events as well.
   */
  override def write(ctx: ChannelHandlerContext, msg: AnyRef, promise: ChannelPromise) = {
    require (connectionRequired || ctx.session.isDefined, "Session is required for this channel and has not been initialized")

    logger.info("Pipeline outbound processing {}", msg)

    (ctx.request, msg) match {
      case (Some(request), response: HttpResponse) =>
        process(request, response)
        ctx.write(msg, promise)

      case _ =>
        ctx.write(msg, promise)
    }
  }

  // Required handler methods
  def process(connection: NeutrinoRequest, response: HttpResponse): Unit = {}
}