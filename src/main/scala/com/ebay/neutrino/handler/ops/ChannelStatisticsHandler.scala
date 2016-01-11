package com.ebay.neutrino.handler.ops

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import com.ebay.neutrino.metrics.Instrumented
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.buffer.{ByteBuf, ByteBufHolder}
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import scala.concurrent.duration._


/**
 * This handler does rudimentary channel-based accounting.
 *
 *
 */
@Sharable
class ChannelStatisticsHandler(upstream: Boolean) extends ChannelDuplexHandler with StrictLogging with Instrumented
{
  import com.ebay.neutrino.util.AttributeSupport._
  import com.ebay.neutrino.util.Utilities.AtomicLongSupport
  import com.ebay.neutrino.metrics.Metrics._

  // Global statistics
  val readBytes    = if (upstream) UpstreamBytesRead else DownstreamBytesRead
  val writeBytes   = if (upstream) UpstreamBytesWrite else DownstreamBytesWrite
  val readPackets  = if (upstream) UpstreamPacketsRead else DownstreamPacketsRead
  val writePackets = if (upstream) UpstreamPacketsWrite else DownstreamPacketsWrite


  @inline def calculateSize(msg: AnyRef) = msg match {
    case data: ByteBuf => data.readableBytes()
    case data: ByteBufHolder => data.content.readableBytes
    case data => 0
  }


  // Log to global (and local) statistics
  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
    val bytes = calculateSize(msg)

    readPackets.mark
    readBytes += bytes
    ctx.statistics.readPackets += 1
    ctx.statistics.readBytes += bytes

    ctx.fireChannelRead(msg)
  }


  override def write(ctx: ChannelHandlerContext, msg: AnyRef, promise: ChannelPromise): Unit = {
    val bytes = calculateSize(msg)

    writePackets.mark
    writeBytes += bytes
    ctx.statistics.writePackets += 1
    ctx.statistics.writeBytes += bytes

    ctx.write(msg, promise)
  }
}

class ChannelStatistics {

  // Collected traffic statistics
  val readBytes     = new AtomicLong()
  val writeBytes    = new AtomicLong()
  val readPackets   = new AtomicLong()
  val writePackets  = new AtomicLong()

  // Channel usage statistics
  val startTime     = System.nanoTime()
  val allocations   = new AtomicInteger()

  // Request statistics
  val requestCount  = new AtomicInteger()
  val responseCount = new AtomicInteger()

  // Helper methods
  def elapsed       = (System.nanoTime()-startTime).nanos
}