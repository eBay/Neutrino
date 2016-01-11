package com.ebay.neutrino.handler

import com.ebay.neutrino.NeutrinoRequest
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http._
import io.netty.util.CharsetUtil

/**
 * Simulated handler representing a more complex handler execution that (eventually) we'll
 * want to insert at this stage (both inbound and outbound)
 *
 */
@Sharable
class ExampleCloseHandler extends ChannelInboundHandlerAdapter with StrictLogging {


  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit =
    msg match {
      case request: NeutrinoRequest if request.uri.startsWith("/close") =>
        // Handle a 'close' request
        val status = HttpResponseStatus.OK
        val buffer = Unpooled.copiedBuffer(s"Handling /close request by closing connection.\r\n", CharsetUtil.UTF_8)
        val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buffer)

        // Set the content-type
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)


      case _ =>
        // Fall through
        ctx.fireChannelRead(msg)
    }
}