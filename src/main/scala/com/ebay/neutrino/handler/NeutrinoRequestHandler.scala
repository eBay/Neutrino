package com.ebay.neutrino.handler

import com.codahale.metrics.annotation.Timed
import com.ebay.neutrino._
import com.ebay.neutrino.channel.NeutrinoEvent
import com.ebay.neutrino.handler.ops.AuditActivity
import com.ebay.neutrino.handler.ops.NeutrinoAuditHandler._
import com.ebay.neutrino.metrics.Instrumented
import com.ebay.neutrino.util.Preconditions
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.handler.codec.http._

import scala.util.{Failure, Success}

/**
 * A handler for managing the HTTP request state of the channel.
 *
 * Since we're using stateful HTTP codecs on both sides of our pipeline channel, we need
 * to sandwich a HTTP-specific handler between them to:
 * 1) Track HTTP state and request/response lifecycle events
 * 2) Perform any channel conditioning.
 *
 * TODO move http-state/modification/logic into a context object
 */
@Sharable
class NeutrinoRequestHandler extends ChannelDuplexHandler with StrictLogging with Instrumented {

  import com.ebay.neutrino.handler.NeutrinoRequestHandler._
  import com.ebay.neutrino.handler.ops.NeutrinoAuditHandler._
  import com.ebay.neutrino.util.AttributeSupport._
  import io.netty.handler.codec.http.HttpHeaderUtil.isKeepAlive


  // Composite channel-future listener
  class ResponseCompleteListener(request: NeutrinoRequest) extends ChannelFutureListener {

    // Handle upstream keep-alive; if not keepAlive, kill it
    override def operationComplete(future: ChannelFuture): Unit = {
      val channel = future.channel()

      // Clean up channel, if request is outstanding
      if (channel.request.isDefined && channel.request.get == request) channel.request = None

      // Handle output write exception
      future.cause match {
        case null =>
          // Close the underlying channel, unless keep-alive is specified
          if (channel.isActive && !isKeepAlive(request.response.get)) channel.close()

        case ex =>
          logger.warn("Unable to write response", future.cause)
          channel.close()
      }

      // Clear the current request and remove references to this request on our channel
      request.complete()
    }
  }


  /**
   * Set the current keep-alive value.
   *
   * Upstream:
   * Only use if both upstream and downstream permit keepalive
   *
   * Downstream:
   * We use the response, if provided, and default to the request's value.
   *
   * @param channel
   * @param request
   * @param response
   */
  def setResponseHeaders(channel: Channel, request: NeutrinoRequest, response: HttpResponse) =
  {
    // Set keep-alive headers; if connection not available just drop packet
    // Also close the underlying channel if it has timed-out
    val elapsed    = channel.statistics.elapsed
    val timeout    = (elapsed > request.session.service.settings.timeouts.sessionCompletion)
    val downstream = isKeepAlive(response)
    val upstream   = request.requestKeepalive && downstream && !timeout

    // Update request/response state with the downstream response copy
    request.response = Option(response)

    // Reset upstream keepalive to match it's expectations, and close downstream if not used anymore
    if (downstream != upstream) setKeepAlive(response, upstream, request.protocolVersion)

    // We're done with the response on the downstream channel; close it if no keepalive
    // TODO move this to the downstream channel stateful pipeline
    if (!downstream) request.downstream map (_.close())
  }


  /**
   * Connect downstream connection.
   *
   * On valid HttpRequest, create a new Connection and add it to the channel provided.
   * Handle transport-tier proxying issues
   *
   * UNUSED - to remove if reference not needed:
   *
   * I think we can just pass everything through.
   *  case _ if ctx.request.isDefined =>
   *    // Ensure we have a valid connection
   *
   *  case _: LastHttpContent =>
   *    // Don't care about these; may be generated after either side has closed the connection
   *    //  return
   *
   *  case _ =>
   *    // Connection not valid; unable to handle msg downstream
   *    ctx.close()
   *    logger.warn("Connection is expected but missing from context; closing channel and swallowing {}", msg)
   *
   * Notes:
   * TODO Transport-convert the incoming request; reset the correct downstream headers
   * TODO decrease max-forwards by 1, if 0 then what?
   *
   * @see http://www.w3.org/Protocols/rfc2616/rfc2616-sec8.html
   * @param ctx
   * @param msg
   */
  @Timed
  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
    import com.ebay.neutrino.util.Utilities._

    logger.info("Message read: {}", msg)
    msg match {
      case http: HttpRequest =>
        // Register the request-level connection entity on the new channel with the Balancer
        val created = NeutrinoRequest.create(ctx.channel, http)

        // Clean up any existing request
        // (ie: if the previous request's final write hasn't completed yet
        ctx.request map (_.complete())

        // Register the new request
        ctx.request = created.toOption

        created match {
          case Success(request) =>
            // Ensure our downstream uses keepalive, if applicable
            if (request.session.service.settings.channel.forceKeepAlive) HttpHeaderUtil.setKeepAlive(request, true)

            // Register the request-level connection entity on the new channel with the Balancer
            // Create a Neutrino request-object and event it
            val event = NeutrinoEvent.RequestCreated(request)
            ctx.channel.audit(AuditActivity.Event(event))
            ctx.fireUserEventTriggered(event)

            // Register the request completion event; will notify on request completion
            request.addListener(new NeutrinoAuditLogger(request))

            // Delegate the request downstream
            ctx.fireChannelRead(request)


          case Failure(ex) =>
            // Handle invalid requests by failure type
            val message = (ex, ex.getCause) match {
              case (_: IllegalArgumentException, se: java.net.URISyntaxException) =>
                s"Invalid request URI provided: ${http.uri}\n\n${se.getMessage}"

              case _ =>
                logger.warn("Unable to create NeutrinoRequest", ex)
                s"Invalid request: ${ex.toString}"
            }

            // Generate an error message
            ctx.sendError(HttpResponseStatus.BAD_REQUEST, message).addListener(ChannelFutureListener.CLOSE)
        }


      case last: LastHttpContent if (ctx.request.isEmpty) =>
        // Probably a pending write after a request-pipeline close. Just swallow

      case _ =>
        ctx.fireChannelRead(msg)
    }
  }

  /**
   * Auto-close unless keep-alive.
   *
   * Update the connection state with the response/headers, setting any appropriate
   * shared state.
   */
  override def write(ctx: ChannelHandlerContext, msg: Object, promise: ChannelPromise): Unit = {

    // Grab the start of the response
    msg match {
      case response: HttpResponse =>
        val session = Preconditions.checkDefined(ctx.session)
        val request = Preconditions.checkDefined(ctx.request)
        logger.info("Writing response: {}", response)

        // Reset upstream keepalive to match it's expectations, and close downstream if not used anymore
        setResponseHeaders(ctx.channel, request, response)

        // Notify a user-event on the response (allows the pipeline to handle...)
        val event = NeutrinoEvent.ResponseReceived(request)
        request.audit(ctx.channel, AuditActivity.Event(event))
        ctx.fireUserEventTriggered(NeutrinoEvent.ResponseReceived(request))

      case _ =>
    }

    // Handle 'last packet'
    msg match {
      case _: LastHttpContent =>
        logger.info("Setting close-listener on last content")

        // Close the underlying channel, unless keep-alive is specified
        val listener = new ResponseCompleteListener(ctx.request.get)
        val unvoid   = promise.unvoid().addListener(listener)
        ctx.write(msg, unvoid)

      case _ =>
        ctx.write(msg, promise)
    }
  }


  /**
   * Called when backpressure options are set.
   */
  override def channelWritabilityChanged(ctx: ChannelHandlerContext): Unit = {
    logger.info("Downstream writeablility changed... is now {}", ctx.channel.isWritable.toString)
    ctx.fireChannelWritabilityChanged()
  }
}


object NeutrinoRequestHandler extends StrictLogging {

  /**
   * Set an appropriate request/response keep-alive based on the version and value
   * provided.
   *
   * We override the default implementation in HttpHeaders.setKeepAlive(self, keepalive)
   * to ensure we send Connection.KeepAlive on HTTP1.1-response to HTTP1.0-request.
   * (as required by apache-bench)
   *
   * @see http://serverfault.com/questions/442960/nginx-ignoring-clients-http-1-0-request-and-respond-by-http-1-1
   *
   * @param message
   * @param keepalive
   * @param version
   */
  def setKeepAlive(message: HttpMessage, keepalive: Boolean, version: HttpVersion) = {
    import HttpHeaderNames.CONNECTION
    import message.headers

    logger.info("Setting keepalive to {} for request version {}", keepalive.toString, version)

    (keepalive, version) match {
      case (true,  HttpVersion.HTTP_1_0)     => headers.set(CONNECTION, HttpHeaderValues.KEEP_ALIVE)
      case (true,  HttpVersion.HTTP_1_1 | _) => headers.remove(CONNECTION)
      case (false, HttpVersion.HTTP_1_0)     => headers.remove(CONNECTION)
      case (false, HttpVersion.HTTP_1_1 | _) => headers.set(CONNECTION, HttpHeaderValues.CLOSE)
    }
  }
}