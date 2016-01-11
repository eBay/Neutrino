package com.ebay.neutrino

import java.util.concurrent.TimeUnit

import com.ebay.neutrino.channel.{NeutrinoSession, NeutrinoService}
import com.ebay.neutrino.handler._
import com.ebay.neutrino.handler.ops.{ChannelStatisticsHandler, ChannelTimeoutHandler, NeutrinoAuditHandler}
import com.ebay.neutrino.metrics.{Instrumented, Metrics}
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.handler.codec.http.HttpServerCodec


/**
 * Neutrino-specific handlers that allow separation of the framework/endpoint's handlers from
 * user-defined handlers (ie: Pipeline Handlers)
 *
 * Ideally, the user would have control over their pipeline customization, and the
 * framework would be able to wrap both operational and transport/framing handlers
 * around in a way that insulates the endpoints.
 *
 * We can solve this one of a couple of ways:
 *
 * 1) Reimplement an interface around ChannelHandlerContext, which in turn reimplements
 *    the ChannelPipeline implementation, to sandbox calls to addFirst() and addLast()
 *    to within a bounded internal subset.
 *
 *    Would call a version of initChannel(customCtx) which would allow the user to
 *    register their own handlers and condition their own application needs, without
 *    gaining access to the underlying pipeline implementation (and consequently the
 *    operational/framework handlers.
 *
 * 2) Invoke the initChannel() method first, and then apply our framework handlers
 *    to the list after the fact.
 *
 *
 * Version 2 is easier in the short term, and is thus implemented. Users may elect from
 * replacing this with Version-1 to provide a truer 'sandbox'
 *
 *
 * Initialize framing handlers.
 * Subclasses should implement, or mix-in, framing implementations
 * For Version-2 (see above) it's critical these implementations install handlers at the
 * front of the pipeline and not rely on addLast()
 */
@Sharable
class NeutrinoServiceInitializer(service: NeutrinoService)
  extends ChannelInboundHandlerAdapter
  with ChannelFutureListener
  with Instrumented
  with StrictLogging
{
  import com.ebay.neutrino.util.AttributeSupport._
  import service.settings

  // Shared handlers
  val stats   = new ChannelStatisticsHandler(true)
  val request = new NeutrinoRequestHandler()
  val audit   = new NeutrinoAuditHandler()
  val user    = new NeutrinoPipelineHandler()
  val timeout = new ChannelTimeoutHandler(settings.timeouts)

  // Stateful handler factories
  @inline def server     = new HttpServerCodec()
  @inline def downstream = new NeutrinoDownstreamHandler()


  override def channelRegistered(ctx: ChannelHandlerContext): Unit =
  {
    // Extract the underlying neutrino pipeline channel
    val channel  = ctx.channel
    val pipeline = ctx.pipeline
    var success  = false

    // Update our downstream metrics
    Metrics.UpstreamTotal.mark
    Metrics.UpstreamOpen += 1

    // TODO support NeutrinoPipeline for annotating handler calls.
    // TODO move into NeutrinoChannel??
    //val pipeline = new NeutrinoPipeline(channel.pipeline())

    // Create new Neutrino managed pipeline for the channel's user-handlers
    // TODO add traffic shaping/management support here

    try {
      // Initialize our framing
      //    pipeline.addLast(new io.netty.handler.logging.LoggingHandler(io.netty.handler.logging.LogLevel.INFO))
      //    pipeline.addLast("logging", new HttpDiagnosticsHandler())
      //
      // Note; we can only use our underlying pipeline, since some of the handlers are composites
      // and don't handle the NeutrinoPipeline well
      pipeline.addLast("timeout",      timeout)
      pipeline.addLast("statistics",   stats)
      pipeline.addLast("http-decoder", server)
      pipeline.addLast("http-state",   request)

      // Add audit pipeline, if configured
      settings.channel.auditThreshold map (_ => pipeline.addLast("audit", audit))

      // Initialize the user-pipeline handlers... (including ChannelInitializers)
      pipeline.addLast("user-pipeline", user)

      // Final handler will reroute inbound to our downstream, as available
      pipeline.addLast("downstream", downstream)

      // Prime the statistics object and hook channel close for proper cleanup
      channel.service = service
      channel.session = NeutrinoSession(channel, service)
      channel.statistics

      // Cleanup on channel close
      channel.closeFuture.addListener(this)

      // Apply our own
      ctx.fireChannelRegistered()
      success = true
    }
    catch {
      case th: Throwable =>
        // Not successful
        logger.warn("Failed to initialize channel {}. Closing {}", ctx.channel(), th)
        ctx.close()
    }
    finally {
      if (pipeline.context(this) != null) pipeline.remove(this)
    }
  }


  /**
   * Handle channel-completion events; cleanup any outstanding channel resources.
   * @param future
   */
  override def operationComplete(future: ChannelFuture): Unit = {
    val stats = future.channel.statistics

    // Cleaning up any outstanding requests
    future.channel.request map (_.complete())
    future.channel.request = None

    // Register a close-listener on the session (to clean up the session state)
    Metrics.UpstreamOpen -= 1

    if (stats.requestCount.get > 0) {
      Metrics.SessionActive -= 1
      Metrics.SessionDuration update(System.nanoTime()-stats.startTime, TimeUnit.NANOSECONDS)
    }

    logger.info("Releasing session {}", this)
  }
}