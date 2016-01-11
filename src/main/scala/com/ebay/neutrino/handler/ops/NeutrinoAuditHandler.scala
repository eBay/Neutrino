package com.ebay.neutrino.handler.ops

import java.util

import com.ebay.neutrino.NeutrinoRequest
import com.ebay.neutrino.channel.NeutrinoEvent
import com.ebay.neutrino.util.Utilities
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.buffer.{ByteBuf, ByteBufHolder}
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.handler.codec.http._

/**
 * Hook the Channel session to audit incoming/outgoing channel events for post-
 * analysis.
 *
 */
@Sharable
class NeutrinoAuditHandler extends ChannelDuplexHandler with StrictLogging
{
  import com.ebay.neutrino.handler.ops.NeutrinoAuditHandler._
  import com.ebay.neutrino.handler.ops.AuditActivity._


  @inline def calculateSize(msg: AnyRef) = msg match {
    case data: ByteBuf => data.readableBytes()
    case data: ByteBufHolder => data.content.readableBytes
    case data => 0
  }

  override def userEventTriggered(ctx: ChannelHandlerContext, event: AnyRef): Unit = {
    ctx.channel.audit(UserEvent(event))
    ctx.fireUserEventTriggered(event)
  }

  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
    ctx.channel.audit(
      msg match {
        case data: HttpRequest     => Request(data)
        case data: HttpResponse    => Response(data)
        case data: LastHttpContent => Content(data.content.readableBytes, true)
        case data: HttpContent     => Content(data.content.readableBytes)
        case data: ByteBuf         => ReadData(data.readableBytes)
      })

    ctx.fireChannelRead(msg)
  }

  override def write(ctx: ChannelHandlerContext, msg: Object, promise: ChannelPromise): Unit = {
    ctx.channel.audit(
      msg match {
        case data: HttpRequest     => Request(data)
        case data: HttpResponse    => Response(data)
        case data: LastHttpContent => Content(data.content.readableBytes, true)
        case data: HttpContent     => Content(data.content.readableBytes)
        case data: ByteBuf         => WriteData(data.readableBytes)
      })

    ctx.write(msg, promise)
  }

  override def flush(ctx: ChannelHandlerContext): Unit = {
    ctx.channel.audit(Flush())
    ctx.flush()
  }

  override def close(ctx: ChannelHandlerContext, future: ChannelPromise): Unit = {
    ctx.channel.audit(Close())
    ctx.close(future)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) = {
    ctx.channel.audit(Error(cause))
    ctx.fireExceptionCaught(cause)
  }
}


object NeutrinoAuditHandler extends StrictLogging {

  import Utilities._


  implicit class NeutrinoAuditRequestSupport(val self: NeutrinoRequest) extends AnyVal {

    /**
     * Retrieve the activity-state, creating if necessary.
     * @return valid state
     */
    def state =
      self.get(classOf[AuditState]) match {
        case None =>
          val state = AuditState()
          self.set(classOf[AuditState], state)
          state

        case Some(state) =>
          state
      }

    // Add the activity provided
    def audit(channel: Channel, data: => AuditActivity) =
      state.add(channel, data)

    // Add the activity provided
    //def audit(channel: Channel, data: Unit => AuditActivity) =
      //state.add(channel, data())

    // Add the activity provided
    def audit(channel: Channel, datafx: PartialFunction[Channel, AuditActivity]) =
      if (datafx.isDefinedAt(channel)) state.add(channel, datafx.apply(channel))

    // Clear the audit state from the request provided
    def clearstate() = self.clear(classOf[AuditState])
  }


  implicit class NeutrinoAuditSupport(val channel: Channel) extends AnyVal {

    // Retrieve the associated state
    def auditstate = AuditState.state(channel)

    // Add the activity provided
    def audit(data: => AuditActivity) = AuditState.audit(channel, data)

    // Clear audit-state, if present
    def clear() = AuditState.clear(channel)
  }


  class NeutrinoAuditLogger(request: NeutrinoRequest) extends ChannelFutureListener {

    // Log requests that exceed the request's threshold
    override def operationComplete(future: ChannelFuture): Unit = {
      // Audit support; if our transaction has taken more than 5 seconds, dump the diagnostics here
      val settings = request.session.service.settings.channel
      val channel  = future.channel

      // Generate audit-records and throw debug
      request.audit(channel, AuditActivity.Event(NeutrinoEvent.ResponseCompleted(request)))

      settings.auditThreshold map { threshold =>
        // Grab the request's data
        val state = request.clear(classOf[AuditState])

        state map { state =>
          if (request.elapsed > threshold)
            logger.warn("Audit state on long running transaction: {}\n{}", channel.toStringExt, state)
          else
            logger.debug("Audit state on transaction: {}\n{}", channel.toStringExt, state)
        }
      }
    }
  }
}


sealed abstract class AuditActivity {
  val time = System.nanoTime
}

object AuditActivity {
  // Supported types
  case class Request(request: HttpRequest) extends AuditActivity
  case class Response(response: HttpResponse) extends AuditActivity
  case class Content(size: Int, last: Boolean=false) extends AuditActivity
  case class ReadData(size: Int) extends AuditActivity
  case class WriteData(size: Int) extends AuditActivity
  case class Error(cause: Throwable) extends AuditActivity
  case class Downstream() extends AuditActivity
  case class DownstreamConnect(success: Boolean) extends AuditActivity
  case class ChannelAssigned(channel: Channel) extends AuditActivity
  case class ChannelException(channel: Channel, cause: Throwable) extends AuditActivity
  case class Event(event: NeutrinoEvent) extends AuditActivity
  case class UserEvent(event: AnyRef) extends AuditActivity
  case class Detail(value: String) extends AuditActivity
  case class Flush() extends AuditActivity
  case class Close() extends AuditActivity
}


case class AuditState() {

  import com.ebay.neutrino.handler.ops.AuditActivity._
  import com.ebay.neutrino.util.AttributeSupport._
  import scala.collection.JavaConversions.asScalaSet

  private val activity = new util.LinkedList[(Channel, AuditActivity)]


  // Add the activity provided
  def add(data: (Channel, AuditActivity)) =
    this.synchronized { activity.add(data) }

  def headerStr(msg: HttpMessage) =
    s"headers = [${msg.headers.names.mkString(",")}]"


  override def toString = {
    val builder = new StringBuilder("AuditState:\n")
    val start   = if (activity.isEmpty) 0L else activity.peekFirst._2.time
    val iter    = activity.iterator

    // Iterate the activity
    while (iter.hasNext) {
      val (channel, item) = iter.next()

      builder
        .append("  ").append(String.format("%9s", ""+(item.time-start)/1000)).append(" micros:\t")
        .append(
          if (channel.service.isDefined) "Sx"+channel.id
          else "0x"+channel.id
        )
        .append('\t')

      builder.append(item match {
        case Request (data: FullHttpRequest)  => s"FullRequest (${data.uri}), ${headerStr(data)}"
        case Request (data)                   => s"Request (${data.uri}), ${headerStr(data)}"
        case Response(data: FullHttpResponse) => s"FullResponse, ${headerStr(data)}"
        case Response(data)                   => s"Response, ${headerStr(data)}"
        case Content(size, true)     => s"LastContent($size)"
        case Content(size, false)    => s"Content($size)"
        case Error(cause: Throwable) => s"Error($cause)"
        case _                       => item.toString
      })

      builder.append('\n')
    }

    builder.toString
  }
}


/**
 * Static helper methods.
 *
 * We define them here to ensure the anonymous lambda classes aren't constructed by
 * the value classes.
 */
object AuditState {

  import com.ebay.neutrino.handler.ops.NeutrinoAuditHandler._
  import com.ebay.neutrino.util.AttributeSupport._


  def request(channel: Channel): Option[NeutrinoRequest] =
    channel.request orElse (channel.session flatMap (_.channel.request))

  def state(channel: Channel): Option[AuditState] =
    request(channel) map (_.state)

  def audit(channel: Channel, data: => AuditActivity) =
    request(channel) map (_.state.add((channel, data)))

  // Clear audit-state, if present
  def clear(channel: Channel) =
    request(channel) map (_.clear(classOf[AuditState]))
}
