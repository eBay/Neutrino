package com.ebay.neutrino.handler

import java.io.IOException
import java.util.ArrayDeque

import com.ebay.neutrino.channel.NeutrinoSession
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.channel._
import io.netty.handler.codec.http._


/**
 * A downstream/client handler for the post-pipeline version of the outer channel handler.
 *
 * In its current form, this handler exists only to manage the current state of the downstream
 * channel, and facilitate management from the outside (ie: during allocation/release)
 *
 */
class NeutrinoClientHandler extends ChannelDuplexHandler with StrictLogging
{
  import ChannelFutureListener.CLOSE_ON_FAILURE
  import com.ebay.neutrino.util.AttributeSupport._

  /** Keep-Alive pending flag */
  @volatile var keepAlive = true

  /** A queue that is used for correlating a request and a response. */
  val queue = new ArrayDeque[HttpMethod]()


  /**
   * Check current state for 'available' and no outstanding request/responses.
   */
  @inline def isAvailable(channel: Channel) =
    channel.isActive() && keepAlive && queue.isEmpty && !inProgress(channel)

  /**
   * Determine if the current handler has any outstanding/in-progress requests or responses.
   */
  @inline def inProgress(channel: Channel) = {
    val stats = channel.statistics
    stats.requestCount.get() != stats.responseCount.get()
  }

  /**
   * Determine if a close is pending on this channel
   * (ie: close has been requested and should be performed on downstream completion)
   */
  @inline def isClosePending() = queue.isEmpty && !keepAlive


  /**
   * Handle incoming response data coming decoded from our response-decoder.
   *
   * Normal case is session-established; just pass thru.
   * If not established, we should fail unless it's the last packet of the response, in which case
   * we can just discard.
   */
  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
    // Track our message state/completion
    if (msg.isInstanceOf[HttpResponse]) {
      // Update our keep-alive flag to false if the response indicates a downstream intention to close
      require(queue.poll() != null)
      keepAlive &= HttpHeaderUtil.isKeepAlive(msg.asInstanceOf[HttpResponse])
    }

    if (msg.isInstanceOf[LastHttpContent]) {
      val stats = ctx.statistics
      require(stats.responseCount.incrementAndGet() == stats.requestCount.get())
    }

    ctx.session match {
      case Some(NeutrinoSession(channel, _)) =>
        channel.write(msg).addListener(CLOSE_ON_FAILURE)

      case None if (msg.isInstanceOf[LastHttpContent]) =>
        // Just discard

      case None if (isAvailable(ctx.channel)) =>
        logger.warn("Read data on unallocated session: {}", msg)

      case None =>
        logger.info("Read data on closed session; closing")
        ctx.close()
    }
  }

  override def channelReadComplete(ctx: ChannelHandlerContext): Unit =
    ctx.session match {
      //case Some(session) if state.isAvailable() =>
        // shouldn't need to flush; downstream handler will on 'last'
      case Some(NeutrinoSession(channel, _)) => channel.flush()
      case None => // Fairly common; just ignore
    }


  /**
   * Handle outgoing HTTP requests.
   */
  override def write(ctx: ChannelHandlerContext, msg: AnyRef, promise: ChannelPromise): Unit =
  {
    msg match {
      case data: HttpRequest =>
        queue.offer(data.method)
        ctx.statistics.requestCount.incrementAndGet()

      case _ =>
    }

    ctx.write(msg, promise)
  }

  override def close(ctx: ChannelHandlerContext, promise: ChannelPromise): Unit = {
    ctx.session match {
      case Some(NeutrinoSession(channel, _)) if channel.isOpen() => channel.close(promise)
      case _ =>
    }
    ctx.close(promise)
  }


  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit =
    (cause, ctx.session) match {
      case (_: IOException, Some(NeutrinoSession(channel, _))) =>
        channel.close()
        ctx.close()

      case (_: IOException, None) =>
        ctx.close()

      case (_, Some(NeutrinoSession(channel, _))) =>
        channel.pipeline.fireExceptionCaught(cause)

      case (_, None) =>
        logger.warn("Unhandled exception on unallocated session", cause)
        ctx.fireExceptionCaught(cause)
    }
}


object NeutrinoClientHandler {

  // Helper methods for downstream channel management
  implicit class NeutrinoClientSupport(val self: Channel) extends AnyVal /*with AttributeKeySupport */{
    //def attr[T](key: AttributeKey[T]): Attribute[T] = self.attr(key)
    //def channel = self

    def handler = self.pipeline.get(classOf[NeutrinoClientHandler])

    /**
     * Stateful introspection into our downstream channel.
     * Determine whether or not it's open and in a consistent state for receiving new connections.
     */
    def isAvailable(): Boolean = self.isActive() && handler.isAvailable(self)

    /**
     *
     */
    def isReusable(): Boolean = self.isActive && !handler.isClosePending()
  }
}


/**
 * We leverage the majority of Netty's original HttpClientCodec to help facilitate some edge
 * cases, including HttpPipelining and HEAD/CONNECT.
 *
 * A combination of {@link HttpRequestEncoder} and {@link HttpResponseDecoder}
 * which enables easier client side HTTP implementation. {@link HttpClientCodec}
 * provides additional state management for <tt>HEAD</tt> and <tt>CONNECT</tt>
 * requests, which {@link HttpResponseDecoder} lacks.  Please refer to
 * {@link HttpResponseDecoder} to learn what additional state management needs
 * to be done for <tt>HEAD</tt> and <tt>CONNECT</tt> and why
 * {@link HttpResponseDecoder} can not handle it by itself.
 *
 * If the {@link Channel} is closed and there are missing responses,
 * a {@link PrematureChannelClosureException} is thrown.
 *
 *
 * Constants; these should be moved to settings.
 *   val maxInitialLineLength = 4096
 *   val maxHeaderSize = 8192
 *   val maxChunkSize  = 8192
 *   val validateHeaders = true
 */
class NeutrinoClientDecoder(queue: ArrayDeque[HttpMethod]) extends HttpResponseDecoder(4096, 8192, 8192, true)
  with StrictLogging
{
  override protected def isContentAlwaysEmpty(msg: HttpMessage): Boolean =
  {
    val statusCode = msg.asInstanceOf[HttpResponse].status.code

    // 100-continue response should be excluded from paired comparison.
    if (statusCode == 100) return true

    // Get the getMethod of the HTTP request that corresponds to the
    // current response.
    if (queue.isEmpty) {
      logger.warn("Empty queue... Error")
    }

    val method = queue.peek()
    val firstChar = method.name.charAt(0)

    firstChar match {
      case 'H' if (HttpMethod.HEAD.equals(method)) =>
        // According to 4.3, RFC2616:
        // All responses to the HEAD request getMethod MUST NOT include a
        // message-body, even though the presence of entity-header fields
        // might lead one to believe they do.
        true

      // The following code was inserted to work around the servers
      // that behave incorrectly.  It has been commented out
      // because it does not work with well behaving servers.
      // Please note, even if the 'Transfer-Encoding: chunked'
      // header exists in the HEAD response, the response should
      // have absolutely no content.
      //
      //// Interesting edge case:
      //// Some poorly implemented servers will send a zero-byte
      //// chunk if Transfer-Encoding of the response is 'chunked'.
      ////
      //// return !msg.isChunked();


      // Successful CONNECT request results in a response with empty body.
      case 'C' if (statusCode == 200 && HttpMethod.CONNECT.equals(method)) =>
        // Proxy connection established - Not HTTP anymore.
        //done = true;
        true

      case _ =>
        super.isContentAlwaysEmpty(msg)
    }
  }
}