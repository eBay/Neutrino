package com.ebay.neutrino.handler

import java.io.IOException
import java.net.{ConnectException, NoRouteToHostException, UnknownServiceException}
import java.nio.channels.ClosedChannelException

import com.ebay.neutrino.NeutrinoRequest
import com.ebay.neutrino.handler.NeutrinoDownstreamHandler.DownstreamConnection
import com.ebay.neutrino.handler.ops.{AuditActivity, NeutrinoAuditHandler}
import com.ebay.neutrino.metrics.Instrumented
import com.ebay.neutrino.util.{Preconditions, ResponseGenerator, Utilities}
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.buffer.{ByteBuf, ByteBufHolder}
import io.netty.channel._
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}

import scala.util.{Failure, Success, Try}


/**
 * Base class for {@link Channel} NeutrinoChannel implementations; built on the EmbeddedChannel
 * framework.
 *
 * NeutrinoChannel (EmbeddedChannel) is:
 * - container for pipeline
 * - dedicated to protocol (ie: HTTP)
 * - tightly coupled with the "request" (not connection).
 * - Knows about each endpoint's framing protocol
 * - is responsible for framing down into the endpoints' frames
 *
 * For simplicity, writing into the EmbeddedChannel handles memory lifecycle and manages virtual
 * session containing...
 */
object NeutrinoDownstreamHandler {

  // Allowed FSM States for the downstream handler
  //  - Available:    Ready to attempt downstream connection
  //  - Connecting:   Downstream connection in progress
  //  - Connected:    Downstream connection established; packets can be sent through as-is
  //  - Closed:       Connection has been closed and can't handle new requests/events
  private sealed trait State
  private case object Available extends State
  private case class Connecting(attempt: NeutrinoDownstreamAttempt) extends State
  private case class Connected(request: NeutrinoRequest, downstream: Channel) extends State
  private case object Closed extends State


  // Supported events
  case class DownstreamConnection(request: NeutrinoRequest, downstream: Try[Channel])


  // This could be externalized
  val generator = new ResponseGenerator()
}


/**
 * Create a new instance with the pipeline initialized with the specified handlers.
 *
 * Note initializers need to be in inner-to-outer order
 */
class NeutrinoDownstreamHandler extends ChannelDuplexHandler with Instrumented with StrictLogging
{
  import com.ebay.neutrino.handler.NeutrinoDownstreamHandler._
  import com.ebay.neutrino.util.AttributeSupport._

  // Mutable data
  private var state: State = Available


  /**
   * Close the underlying session and clean up any outstanding state.
   */
  private def close(ctx: ChannelHandlerContext) = {
    state = Closed
    if (ctx.channel.isOpen) ctx.close()
  }

  // These are intended to cascade down through any remaining incoming (upstream) handlers
  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    // Handle state cleanup
    state match {
      case Connecting(attempt) if (!attempt.pending.isEmpty) =>
      case _ =>
    }

    state = Closed
    ctx.fireChannelInactive
  }

  override def channelWritabilityChanged(ctx: ChannelHandlerContext): Unit =
    ctx.fireChannelWritabilityChanged

  /**
   * Process reads against the bottom of our pipeline. These will be sent across to the downstream.
   *
   * Our possible states during execution of this are:
   * - Downstream connected, messages pending:  Flush pending, Send packet through
   * - Downstream connected, no pending: Send packet through
   * - Not connected,
   */
  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {

    // Establish/terminate downstream connections here
    (state, msg) match {
      case (Available, data: NeutrinoRequest) =>
        // Initiate our downstream connection attempt
        state = Connecting(new NeutrinoDownstreamAttempt(ctx, data))
        metrics.counter("created") += 1

      case (Connecting(attempt), data: HttpContent) =>
        attempt.pending = attempt.pending :+ data

      case (Connected(request, downstream), data: HttpContent) =>
        downstream.write(msg).addListener(new NeutrinoDownstreamListener(ctx, data))

      case _ =>
        logger.warn("Received an unexpected message for downstream when channel was {} - ignoring: {}", state, msg)
    }
  }

  override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
    state match {
      case Connected(request, downstream) => downstream.flush()
      case _ => // ?? error here??
    }
    ctx.fireChannelReadComplete()
  }


  /**
   * Handle our request/downstream-channel lifecycle events.
   *
   * This is pretty hacky; see if we can find a better way...
   */
  override def userEventTriggered(ctx: ChannelHandlerContext, event: AnyRef): Unit = {

    event match {
      case DownstreamConnection(request, Success(channel)) if (request.downstream.isEmpty) =>
        // Channel was connected, but is no longer active; either side timed out

      case DownstreamConnection(request, Success(channel)) =>
        state match {
          /**
           * Flush the pending operations.
           *
           * Note that this is externally-called only (not called from within this class).
           * This allows us to rely on the caller to provide required synchronization, and
           * implicitly rely on the calling channel's thread-safely.
           */
          case Connecting(attempt) =>
            require(request.downstream.isDefined, "Downstream should be set")
            logger.debug("Downstream established. Flushing {} message(s) from {}", attempt.pending.size.toString, request.requestUri)
            metrics.counter("established") += 1

            // Flush any pending messages to the channel provided
            if (attempt.pending.nonEmpty) {
              attempt.pending foreach { msg =>
                channel.write(msg).addListener(new NeutrinoDownstreamListener(ctx, msg))
              }
              channel.flush()
              attempt.pending = Seq.empty
            }

            // Store the successful downstream
            state = Connected(request, channel)


          case Closed =>
            // Channel was closed between connect-request and now.
            // Do we have to explicitly release the request and clean up? Or can we just ignore and implicitly do it

          case other =>
            throw new IllegalStateException(s"Should have been Connecting or Closed, was $state")
        }


      case DownstreamConnection(request, Failure(ex)) =>
        // Generate additional diagnostics on unexpected failures
        val metricname = ex match {
          case ex: UnknownServiceException => "rejected.no-pool"
          case ex: ConnectTimeoutException => "rejected.timeout"
          case ex: ConnectException        => "rejected.no-connect"
          case _ => "rejected"
        }

        // Reset our state to take future connections
        state = Available

        // Propagate the failure to our exception handling
        metrics.counter(metricname) += 1
        ctx.pipeline.fireExceptionCaught(ex)


      case _ =>
        ctx.fireUserEventTriggered(event)
    }
  }

  /**
   * Intercept the write; release our downstream on completion of the response.
   *
   * @param ctx
   * @param msg
   * @param promise
   */
  override def write(ctx: ChannelHandlerContext, msg: AnyRef, promise: ChannelPromise): Unit =
    msg match {
      case _: LastHttpContent =>
        require(state.isInstanceOf[Connected], "State should be connected")
        val request = Preconditions.checkDefined(ctx.request, "Request should be active in the session")

        // On completion of response, release the current request
        request.pool.release(false)

        // Write and flush the downstream message
        // (explicitly flush here, because our pool-release may prevent downstream flush
        // from finding our way up)
        ctx.writeAndFlush(msg, promise)

        // Ensure it's in-progress
        state match {
          case Available | _:Connected =>
            // Normal operation; return state to 'ready'
            state = Available
            metrics.counter("completed.") += 1

          case Connecting(attempt) =>
            // Terminated abnormally; we won't be able to recover so just close
            close(ctx)
            metrics.counter("completed.premature") += 1

          case Closed =>
            // Skip - already closed

          case _ =>
            throw new IllegalStateException(s"ResponseCompleted should only occur when Available/Connecting/Connected (was $state)")
        }

      case _ =>
        // All other message types
        ctx.write(msg, promise)
    }
  

  /**
   * These may not be necessary; in lieu of anything better to do, we'll send the eventing
   * back to our original upstream (inbound).
   *
   * Recognized exception:
   * - UnknownServiceException:   No pool available/resolved
   * - NoRouteToHostException:    No nodes available in pool
   * - ConnectException:          Unable to connect to downstream
   *
   * @param ctx
   * @param cause
   */
  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit =
    cause match {
      case ex: NoSuchElementException if (ex.getMessage() == "None.get") =>
        // TODO fix this case; see NEUTRINO-JIRA
        metrics.counter("exception.NoSuchElementException") += 1

      case ex: UnknownServiceException =>
        // Unable to resolve a real pool. May be able to recover; ignore and propagate
        val response = generator.generate(SERVICE_UNAVAILABLE, cause.getMessage)
        ctx.writeAndFlush(response)

      case ex: NoRouteToHostException =>
        // Unable to get a healthy member from the resolved pool.
        val response = generator.generate(SERVICE_UNAVAILABLE, cause.getMessage)
        ctx.writeAndFlush(response)

      case ex: ConnectException =>
        // Unable to connect to downstream (including timeout)
        // Do we need to close on this this??
        val response = generator.generate(GATEWAY_TIMEOUT, cause.getMessage)
        ctx.writeAndFlush(response)

      case ex: ClosedChannelException =>
        // Additional write attempts on a closed connection; just ignore and close
        close(ctx)

      case ex: IOException =>
        // We won't be able to recover, so close the session/channel
        close(ctx)

      case ex =>
        logger.warn("Unhandled exception in channel session", cause)
        ctx.fireExceptionCaught(cause)
    }
}


/**
 * This supports a three-stage connection establishment process.
 *
 * 1) Request downstream from pool
 * 2) On invalid downstream, quit (unable to send)
 * 3) On valid downstream, attempt to send the pending payload message
 * 4) On invalid send return to 1)
 * 5) On valid send, channel is open and we can continue
 *
 * Note that 3) is required to determine whether the channel is actually open, or just hasn't
 * detected the close yet. A send-attempt will fail if the channel is actually closed.
 *
 * @param ctx
 * @param request
 */
class NeutrinoDownstreamAttempt(ctx: ChannelHandlerContext, request: NeutrinoRequest) extends ChannelFutureListener with StrictLogging {

  import NeutrinoAuditHandler._
  import com.ebay.neutrino.util.Utilities._

  // Cache outstanding messages pending completion of our connection
  var pending = Seq.empty[HttpContent]

  // Apply back-pressure to the incoming channel
  // Should we mess around with the other settings to minimize in-progress inbound buffer too?
  ctx.channel.config.setAutoRead(false)

  // Kick off the negotiation
  establish()

  // If not established, or force is provided, renegotiate the endpoint
  // Grab the connection's current pool, and see if we have a resolver available
  // If downstream not available, attempt to connect one here
  import request.session.service.context
  def establish() =
    request.connect() onComplete {
      case Success(endpoint) =>
        // Grab our first available result, if possible, and hook configuration of endpoint on success
        // Attempt to send the pending payload over the channel.
        endpoint.writeAndFlush(request).addListener(this)

      case failure @ Failure(ex) =>
        // Flat-out failed to return a viable channel. This is a pool-resolver failure

        // Resend a user-event notification of completion (to process within the thread-env)
        //?? upstream.setAutoRead(true)
        ctx.pipeline.fireUserEventTriggered(DownstreamConnection(request, failure))
    }


  /**
   * Handle any downstream channel-errors here.
   * @param future
   */
  override def operationComplete(future: ChannelFuture): Unit = {
    import future.channel

    future.isSuccess match {
      case true =>
        // Audit-log the completion
        channel.audit(AuditActivity.ChannelAssigned(channel))

        // Resend a user-event notification of completion (to process within the thread-env)
        ctx.pipeline.fireUserEventTriggered(DownstreamConnection(request, Success(channel)))

        // Downstream endpoint resolved; turn the tap back on
        ctx.channel.config.setAutoRead(true)


      case false =>
        // Audit-log the completion
        channel.audit(AuditActivity.ChannelException(channel, future.cause))

        logger.info("Downstream failed on connect - closing channel {} and bound to {} and retrying", channel.toStringExt, ctx.channel.toStringExt)
        channel.close

        // Release the request back to the pool (but leave the pool resolved)
        request.pool.release(false)

        // Reattempt to establish a connection with a new channel
        establish()
    }
  }

}


class NeutrinoDownstreamListener(ctx: ChannelHandlerContext, val payload: HttpContent) extends ChannelFutureListener with StrictLogging
{
  import Utilities._
  import com.ebay.neutrino.handler.ops.NeutrinoAuditHandler.NeutrinoAuditSupport

  import scala.concurrent.duration._

  val start = System.nanoTime()

  val size =
    payload match {
      case data: ByteBuf => data.readableBytes()
      case data: ByteBufHolder => data.content.readableBytes
      case data => 0
    }


  /**
   * Handle any downstream channel-errors here.
   * @param future
   */
  override def operationComplete(future: ChannelFuture): Unit = {
    import future.channel

    channel.audit(
      AuditActivity.Detail(s"DownstreamWrite: $size = ${future.isSuccess}, cause ${future.cause}"))

    future.cause match {
      case null =>
        // Successful send; ignore
        // TODO add audit record

      case ex: ClosedChannelException if (!channel.isActive && payload.isInstanceOf[LastHttpContent]) =>
        // Downstream channel was already closed; ensure we're closed too
        logger.debug("Downstream write failed - channel was already closed before final packet. Closing our upstream.")
        ctx.close()

      case ex: ClosedChannelException if (!channel.isActive) =>
        // Downstream channel was already closed; ensure we're closed too
        logger.info("Downstream write failed - channel was already closed. Closing our upstream.")
        ctx.close() //ctx.pipeline.fireExceptionCaught(future.cause)

      case ex =>
        // Handle downstream write-failure
        val elapsed = ((System.nanoTime()-start)/1000) micros;
        logger.info("Downstream write failed - closing channel {} (Failed with {} in {}, size {}, packet # {})", channel.toStringExt, future.cause, elapsed, size.toString)
        channel.close

        // Also propagate the IO-failure event to the session for better handling
        ctx.pipeline.fireExceptionCaught(future.cause)
    }
  }
}