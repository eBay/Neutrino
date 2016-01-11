package com.ebay.neutrino

import java.net.{InetAddress, InetSocketAddress, SocketAddress}
import com.ebay.neutrino.util.Utilities

import scala.concurrent.Future
import scala.util.{Failure, Success}

import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.bootstrap.{Bootstrap, ServerBootstrap}
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.{NioServerSocketChannel, NioSocketChannel}
import io.netty.util.AttributeKey


/**
 * Create a simulated Layer-4 Proxy application; we'll use SSL to test an
 * end-to-end.
 *
 *
 * I used the following to test:
 * > curl -k -H 'Host: www.paypal.com' "https://localhost:7000"
 *
 */
object PacketProxy extends App {

  // Hard-code a configuration
  //val settings = LoadBalancerSettings(Configuration.load("proxy.conf"))

  val supervisor = new NioEventLoopGroup(1)
  val workers = new NioEventLoopGroup()

  new ServerBootstrap()
    .channel(classOf[NioServerSocketChannel])
    .group(supervisor, workers)
    .childHandler(new InboundHandler(workers))
    .childOption[java.lang.Boolean](ChannelOption.TCP_NODELAY, true)
    .childOption[java.lang.Boolean](ChannelOption.AUTO_READ, false)
    .bind(InetAddress.getLocalHost(), 7000)
    //.bind(InetAddress.getByName("0.0.0.0"), 8081)
    .sync()

}

@Sharable
class InboundHandler(workers: EventLoopGroup) extends ChannelInboundHandlerAdapter with StrictLogging {

  val Downstream = new InetSocketAddress("www.paypal.com", 443)
  val DownstreamKey = AttributeKey.valueOf[Channel]("balancer")

  /**
   * Setup the incoming channel once established.
   */
  override def channelRegistered(ctx: ChannelHandlerContext): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    println(s"Initializing Proxy Channel $ctx")

    // Establish a downstream connection
    val channel = ctx.channel
    val downstream = connect(channel, Downstream)

    downstream onComplete {
      case Success(downstream) =>
        println(s"Connected downstream $downstream")
        ctx.attr(DownstreamKey).set(downstream)
        channel.config.setAutoRead(true)
        channel.read()

      case Failure(ex) =>
        logger.warn("Downstream connection failed.", ex)
    }

    ctx.fireChannelRegistered()
  }

  /**
    */
  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef) {
    println("Writing packet from upstream to downstream...")
    ctx.attr(DownstreamKey).get().writeAndFlush(msg)
    //ctx.fireChannelRead(msg)
  }


  def connect(channel: Channel, address: SocketAddress): Future[Channel] = {
    import Utilities._

    new Bootstrap()
      .channel(classOf[NioSocketChannel])
      .group(workers)
      .handler(new OutboundHandler(channel))
      .option[java.lang.Boolean](ChannelOption.TCP_NODELAY, true)
      .connect(address)
      .future
  }
}

class OutboundHandler(upstream: Channel) extends ChannelInboundHandlerAdapter {

  override def channelRegistered(ctx: ChannelHandlerContext): Unit = {
    println("Registered downstream")
    ctx.fireChannelRegistered()
  }

  /**
   */
  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef) {
    println("Writing packet from downstream to upstream...")
    upstream.writeAndFlush(msg)
    //ctx.fireChannelRead(msg)
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    println("Downstream closing..")
    upstream.close()
    ctx.fireChannelInactive()
  }
}