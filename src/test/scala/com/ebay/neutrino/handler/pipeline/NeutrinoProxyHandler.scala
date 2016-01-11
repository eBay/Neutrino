package com.ebay.neutrino.handler.pipeline

import java.net.SocketAddress

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.annotation.{Metered, Timed}
import com.ebay.neutrino.handler.MetricsAnnotationRegistry
import com.ebay.neutrino.metrics.Metrics
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.channel._
import nl.grons.metrics.scala.{InstrumentedBuilder, Meter, Timer}


/**
 * Default proxy implementations of our common ChannelHandler types.
 * Intended for use with overriding default Netty channel internals implementation.
 *
 * @param delegate
 */
class NeutrinoProxyHandler[T <: ChannelHandler](delegate: T) extends DelegateHandler(delegate) with NeutrinoHandler


final class NeutrinoInboundProxyHandler[T <: ChannelInboundHandler](self: T)(implicit val metricRegistry: MetricRegistry)
  extends NeutrinoProxyHandler[T](self)
  with NeutrinoInboundProxyAdapter[T]
  with NeutrinoHandler


final class NeutrinoOutboundProxyHandler[T <: ChannelOutboundHandler](self: T)(implicit val metricRegistry: MetricRegistry)
  extends NeutrinoProxyHandler[T](self)
  with NeutrinoOutboundProxyAdapter[T]
  with NeutrinoHandler


final class NeutrinoDuplexProxyHandler[T <: ChannelInboundHandler with ChannelOutboundHandler](self: T)(implicit val metricRegistry: MetricRegistry)
  extends NeutrinoProxyHandler[T](self)
  with NeutrinoInboundProxyAdapter[T]
  with NeutrinoOutboundProxyAdapter[T]
  with NeutrinoHandler


object NeutrinoProxyHandler {
  import io.netty.channel.{ChannelInboundHandler => Inbound, ChannelOutboundHandler => Outbound}

  implicit val registry = Metrics.metricRegistry
  val annotationRegistry = new MetricsAnnotationRegistry

  /**
   * Create a new operational ChannelHandler adapter to provide operational delegation.
   *
   * Due to the runtime-typecheck requirements of the inbound/outbound handlers,
   * we have to create with the explicit delegate typing.
   *
   * @param delegate
   * @return
   */
  def apply(delegate: ChannelHandler): NeutrinoHandler =
    delegate match {
      case both: Inbound with Outbound => new NeutrinoDuplexProxyHandler(both)
      case inbound: Inbound            => new NeutrinoInboundProxyHandler(inbound)
      case outbound: Outbound          => new NeutrinoOutboundProxyHandler(outbound)
      case other                       => new ChannelHandlerAdapter with NeutrinoHandler
    }
}


sealed trait NeutrinoInboundProxyAdapter[T <: ChannelInboundHandler]
  extends DelegateInboundAdapter[T]
  with NeutrinoInstrumentation[T]
  with NeutrinoHandler
{
  // Determine the instrumentation and cache the function pointers
  val fnChannelRegistered   = instrument("channelRegistered")
  val fnChannelUnregistered = instrument("channelUnregistered")
  val fnChannelActive       = instrument("channelActive")
  val fnChannelInactive     = instrument("channelInactive")
  val fnChannelRead         = instrument("channelRead")
  val fnUserEventTriggered  = instrument("userEventTriggered")


  override def channelRegistered(ctx: ChannelHandlerContext): Unit = {
    fnChannelRegistered { delegate.channelRegistered(ctx) }
  }

  override def channelUnregistered(ctx: ChannelHandlerContext): Unit =
    fnChannelUnregistered { delegate.channelUnregistered(ctx) }

  override def channelActive(ctx: ChannelHandlerContext): Unit =
    fnChannelActive { delegate.channelActive(ctx) }

  override def channelInactive(ctx: ChannelHandlerContext): Unit =
    fnChannelInactive { delegate.channelInactive(ctx) }

  // TODO - JIRA annotation
  // TODO - p4 PoC on compiler macro
  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit =
    fnChannelRead { delegate.channelRead(ctx, msg) }

  override def userEventTriggered(ctx: ChannelHandlerContext, evt: AnyRef): Unit =
    fnUserEventTriggered { delegate.userEventTriggered(ctx, evt) }
}


sealed trait NeutrinoOutboundProxyAdapter[T <: ChannelOutboundHandler]
  extends DelegateOutboundAdapter[T]
  with NeutrinoInstrumentation[T]
  with NeutrinoHandler
{
  // Determine the instrumentation and cache the function pointers
  val fnWrite     = instrument("write")
  val fnConnect   = instrument("connect")


  override def write(ctx: ChannelHandlerContext, msg: scala.Any, promise: ChannelPromise): Unit =
    fnWrite { delegate.write(ctx, msg, promise) }

  override def connect(ctx: ChannelHandlerContext, remoteAddress: SocketAddress, localAddress: SocketAddress, promise: ChannelPromise): Unit =
    fnConnect { delegate.connect(ctx, remoteAddress, localAddress, promise) }
}


sealed trait NeutrinoInstrumentation[T] extends InstrumentedBuilder with StrictLogging {
  import InstrumentationSupport._

  type Wrapper = (Unit => Unit)

  def delegate: T


  /** These are simpler wrapper functions but recreate the metric object on each invocation.
   *  Ideally, we just want to create once and reuse the handle.
   *
  // Wrap a metering/counting metric around the function provided
  def meter[T](metricname: String)(f: => T): T =
    new Meter(metrics.registry.meter(metricname)).metered { f }

  // Wrap a timing/stopwatch metric around the function provided
  def time[T](metricname: String)(f: => T): T =
    new Timer(metrics.registry.timer(metricname)).time { f }
  */


  // Wrap a metering/counting metric around the function provided
  def wrapMeter(metricname: String): Wrapper = {
    val metric = new Meter(metrics.registry.meter(metricname))
    (f: Unit) => metric.metered { f }
  }

  // Wrap a timing/stopwatch metric around the function provided
  def wrapTimer(metricname: String): Wrapper = {
    val metric = new Timer(metrics.registry.timer(metricname))
    (f: Unit) => metric.time { f }
  }


  def instrument(methodName: String): Wrapper = {
    val clazz = delegate.getClass
    val annotations = NeutrinoProxyHandler.annotationRegistry.getAnnotations(clazz, methodName)

    def annotationName(defaultName: String, absolute: Boolean) =
      defaultName match {
        case null | "" if absolute => methodName
        case null | ""             => MetricRegistry.name(clazz, methodName)
        case name if absolute      => name
        case name                  => MetricRegistry.name(clazz, name)
      }

    annotations.foldLeft((f: Unit) => f) { (fn, annotation) =>
      logger.info("Annotating metrics for {}::{} of type {}", clazz, methodName, annotation)

      annotation match {
        case ann: Timed   => wrapTimer(annotationName(ann.name, ann.absolute))
        case ann: Metered => wrapMeter(annotationName(ann.name, ann.absolute))
        case ann          => logger.warn("Unsupported annotation {}", ann); fn
      }
    }
  }
}


object InstrumentationSupport {

  implicit class MeterSupport(val self: Meter) extends AnyVal {
    def metered[A](f: => A): A = {
      self.mark()
      f
    }
  }
}