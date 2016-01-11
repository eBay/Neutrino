package com.ebay.neutrino.integ

import java.net.InetAddress

import com.ebay.neutrino.integ.NettyEchoServer._
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http._
import io.netty.util.{CharsetUtil, ReferenceCountUtil}

/**
 * Implementation of a simple HTTP echo server using Netty
 */
class NettyEchoServer(host: InetAddress=InetAddress.getLocalHost(), port: Int=DefaultPort) {

  val supervisor = new NioEventLoopGroup(1)
  val workers    = new NioEventLoopGroup()
  val bootstrap  = new ServerBootstrap()
    .channel(classOf[NioServerSocketChannel])
    .group(supervisor, workers)
    .childHandler(new NettyEchoInitializer())
    .childOption[java.lang.Boolean](ChannelOption.TCP_NODELAY, true)

  private var channel: Channel = _

  def start() = {
    if (channel == null) channel = bootstrap.bind(host, port).sync().channel()
    channel.closeFuture
  }

  def shutdown() =
    if (channel != null) {
      channel.close
      channel.closeFuture.sync
      channel = null
    }
}

object NettyEchoServer {

  val DefaultPort = 8081
}


@Sharable
class NettyEchoInitializer extends ChannelInboundHandlerAdapter {
  import io.netty.handler.codec.http.HttpHeaderNames._
  import io.netty.handler.codec.http.HttpVersion._

  // TODO cache the underying HTTP-raw-bytebuf
  val echoBuffer = Unpooled.copiedBuffer(s"Echo!", CharsetUtil.UTF_8)


  def createResponse(content: ByteBuf, keepalive: Boolean): FullHttpResponse = {
    val response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.OK, content)
    val headers = response.headers

    // Set appropriate headers
    headers.set(CONTENT_TYPE, "text/plain; charset=UTF-8")
    HttpHeaderUtil.setContentLength(response, content.readableBytes())
    HttpHeaderUtil.setKeepAlive(response, keepalive)

    response
  }


  override def channelRegistered(ctx: ChannelHandlerContext) = {
    // Inject an HTTP handler upstream
    ctx.pipeline.addFirst(new HttpServerCodec(), new HttpObjectAggregator(2*1000*1000))
  }


  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef) = {
    msg match {
      case request: HttpRequest =>
        // Build an echo response
        val keepalive = HttpHeaderUtil.isKeepAlive(request)
        val content   = echoBuffer.duplicate().retain()
        val response  = createResponse(content, keepalive)
        val future    = ctx.writeAndFlush(response)

        // If not keepalive, close the connection as soon as the error message is sent.
        if (!keepalive) future.addListener(ChannelFutureListener.CLOSE)

      case _ =>
        ReferenceCountUtil.release(msg)
        ctx.close()
    }

    // Release the request
    ReferenceCountUtil.release(msg)
  }


  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) = {}
}


/**
 * TODO start programmatically
 */
object NettyEchoServerApp extends App {

  //ResourceLeakDetector.setLevel(Level.PARANOID)

  val server = new NettyEchoServer()
  server.start().sync()
}
