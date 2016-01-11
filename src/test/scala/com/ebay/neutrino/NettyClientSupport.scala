package com.ebay.neutrino

import java.net.InetAddress

import io.netty.bootstrap.{Bootstrap, ServerBootstrap}
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.{NioServerSocketChannel, NioSocketChannel}
import io.netty.handler.codec.http._
import io.netty.handler.timeout.{ReadTimeoutException, WriteTimeoutException}
import io.netty.util.CharsetUtil

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, Future, Promise}

/**
 * Created by cbrawn on 3/3/15.
 *
 */
trait NettyTestSupport {

  import io.netty.handler.codec.http.HttpVersion._

  // Constants
  val DefaultHost = InetAddress.getLocalHost
  val DefaultPort = 9000

  // Shared worker-pools
  val supervisor = new NioEventLoopGroup(1)
  val workers = new NioEventLoopGroup()


  @Sharable
  class MessageHandler(message: String) extends ChannelInboundHandlerAdapter {

    override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef) = {
      // Create a message
      val buffer   = Unpooled.copiedBuffer(message+"\r\n", CharsetUtil.UTF_8)
      val response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.OK, buffer)

      //response.headers.set(Names.CONTENT_TYPE, "text/plain; charset=UTF-8")
      ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
    }

    override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) =
      cause match {
        case _: ReadTimeoutException =>
        case _: WriteTimeoutException =>
      }
  }
}


trait NettyClientSupport extends NettyTestSupport {

  // Full request/response initializer
  @Sharable
  class HttpClientInitializer(aggregate: Boolean) extends ChannelInitializer[Channel] {

    val response = Promise.apply[FullHttpResponse]()

    def initChannel(channel: Channel) = {
      channel.pipeline.addLast(new HttpClientCodec())
      if (aggregate) channel.pipeline.addLast(new HttpObjectAggregator(1000000))
      channel.pipeline.addLast(new ResponseHandler(response))
    }
  }

  // Simple response-handler which notifies the promise when response is completed
  class ResponseHandler(promise: Promise[FullHttpResponse]) extends ChannelDuplexHandler {

    // Default implementation; buffer the incoming data
    override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit =
      msg match {
        case response: FullHttpResponse => promise.success(response)
        case _ =>
      }

    override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) = {
      if (!promise.isCompleted) promise.failure(cause)
    }
  }

  //
  class InboundMsgBuffer extends ChannelInboundHandlerAdapter {
    var inbound = Seq.empty[AnyRef]

    override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
      inbound = inbound :+ msg
    }
  }


  /**
   * In-progress connection wrapper, for facilitating mediation to connection
   *
   * @param channel
   * @param response
   */
  case class Connection(channel: Channel, response: Future[FullHttpResponse]) {

    val start = System.currentTimeMillis
    def elapsed = (System.currentTimeMillis-start).millis

    // Send the request message-portion provided
    def send(message: HttpObject): ChannelFuture = channel.pipeline.writeAndFlush(message)

    // Wait for the HTTP response, up to the time specified
    def result(timeout: Duration) = Await.result(response, timeout)
    def ready(timeout: Duration) = Await.ready(response, timeout)
  }


  case class HttpClient(host: InetAddress=DefaultHost, port: Int=DefaultPort, aggregate: Boolean=true) {

    val workers = new NioEventLoopGroup()
    def initialzer = new HttpClientInitializer(aggregate)

    def send(request: HttpRequest): Connection = {
      val init = initialzer
      val boot = new Bootstrap().channel(classOf[NioSocketChannel]).group(workers).handler(init).connect(host, port)
      val conn = Connection(boot.sync().channel, init.response.future)

      conn.send(request).sync()
      conn
    }
  }
}


trait NettyServerSupport extends NettyTestSupport {

  // Full request/response initializer
  @Sharable
  class HttpServerInitializer(handlers: ChannelHandler*) extends ChannelInitializer[Channel] {
    def initChannel(channel: Channel) = {
      channel.pipeline.addLast(new HttpServerCodec())
      channel.pipeline.addLast(new HttpObjectAggregator(1000000))
      channel.pipeline.addLast(handlers:_*)
    }
  }


  def startup(handlers: Seq[ChannelHandler]): ChannelFuture =
    startup(new HttpServerInitializer(handlers:_*))


  def startup(initializer: ChannelInitializer[Channel], host: InetAddress=InetAddress.getLocalHost, port: Int=9000): ChannelFuture =
  {
    val channel = new ServerBootstrap()
      .channel(classOf[NioServerSocketChannel])
      .group(supervisor, workers)
      .childHandler(initializer)
      .childOption[java.lang.Boolean](ChannelOption.TCP_NODELAY, true)
      .bind(host, port)
      .sync()

    assert(channel.isDone && channel.isSuccess)
    channel
  }

}