package com.ebay.neutrino

import java.util.concurrent.TimeUnit

import com.ebay.neutrino.config.{Configuration, LoadBalancer}
import com.ebay.neutrino.util.ResponseUtil
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.handler.codec.http._
import io.netty.util.{AsciiString, AttributeKey}


/**
 * Create a simple Server application
 * (one that doesn't proxy to any downstream applications)
 *
 * val endpoint = EndpointConfig(8081)
 * val vip      = VipSettings()
 * val server   = VirtualServer("localhost", 8081)
 *
 * // Example pluggable handler(s)
 * val pipeline = PipelineInitializer(new PipelineHandler())
 * core.initialize(core.http.service(vip, pipeline))
 * core.register(core.http.client(server))
 */
object Server extends App {

  // Hard-code a configuration
  val config = Configuration.load("server.conf")

  // Create a new balancer
  val core = NeutrinoCore(config)

  // Start running. This will run until the process is interrupted...
  core.configure(LoadBalancer(config))
  core.start()
}


@Sharable
class LoremIpsumGenerator extends ChannelInboundHandlerAdapter with StrictLogging {
  import scala.concurrent.duration._

  val MinDelay = 10 milliseconds


  // Introduce a server delay on the incoming request
  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef) = {

    msg match {
      case data: HttpRequest =>
        val delay = 1 millisecond //Random.nextInt(1500) milliseconds

        // If delay is too small, just send now
        if (delay < MinDelay)
          sendResponse(ctx, delay, 8000)
        else
          schedule(ctx, delay, sendResponse(ctx, delay, 8000))


      case data: HttpContent =>
        // Ignore

      case _ =>
        logger.warn("Unexpected HTTP Content: {}", msg)
    }
  }


  def schedule(ctx: ChannelHandlerContext, delay: FiniteDuration, fx: => Unit) = {
    val task = new Runnable { def run(): Unit = fx }
    val loop = ctx.channel.parent.eventLoop
    loop.schedule(task, delay.toMillis, TimeUnit.MILLISECONDS)
  }


  // Prep some data sizes
  //val data = new Array[ByteBuf](10)
  //data(0) = Unpooled.

  val DelayHeader = new AsciiString("X-DELAY")

  val original =
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.".getBytes()

  val buffers = (0 until 10) map { offset =>
    val size   = 1024 << offset
    val buffer = Unpooled.directBuffer(size, size)
    while (buffer.isWritable) {
      buffer.writeBytes(original, 0, Math.min(buffer.writableBytes(), original.length))
      // if (buffer.writableBytes() >= 2) buffer.writeBytes("\r\n".getBytes)
    }
    buffer
  } //toArray

  /**
   * Create and send a custom response.
   *
   * @param ctx
   * @param delay
   * @return
   */
  def sendResponse(ctx: ChannelHandlerContext, delay: FiniteDuration, contentSize: Int) = {

    val options  = ServerOptions.options(ctx.channel)

    val small    = buffers(0)
    val medium   = buffers(4)
    val large    = buffers(8)
    //val content  = Unpooled.wrappedBuffer(small, large, small).retain()
    val content  = Unpooled.wrappedBuffer(medium).retain()
    val response = ResponseUtil.generate(HttpResponseStatus.OK, content)

    // Add additional diagnostic headers
    response.headers.add(DelayHeader, delay.toString)

    // Close if required
    options.requestCount -= 1
    if (options.requestCount == 0) HttpHeaderUtil.setKeepAlive(response, false)

    // Do the write
    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
  }

}


/**
 * Hard-coded options.
 */
// Set to 0/-ve for no limit on requests
case class ServerOptions(var requestCount: Int)

object ServerOptions {

  // Constants
  val MaxRequestCount = 100

  val Key = AttributeKey.newInstance[ServerOptions]("server-options")

  // Helper methods
  def options(channel: Channel): ServerOptions = {
    val attr = channel.attr(Key)
    attr.setIfAbsent(ServerOptions(MaxRequestCount))
    attr.get()
  }

}