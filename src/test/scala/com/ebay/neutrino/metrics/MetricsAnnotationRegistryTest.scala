package com.ebay.neutrino.health

import com.codahale.metrics.annotation.{Gauge, Metered, Timed}
import com.ebay.neutrino.handler.MetricsAnnotationRegistry
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandler}
import org.scalatest.{FlatSpec, Matchers}


class MetricsAnnotationRegistryTest extends FlatSpec with Matchers {
  behavior of "Parsing Metrics Annotations"


  it should "skip class with no annotations" in {
    abstract class Test extends ChannelInboundHandler {
      override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef) {}
    }

    // No annotations for the inbound-methods
    val registry = new MetricsAnnotationRegistry
    registry.getAnnotations(classOf[Test], "channelRegistered") shouldBe empty
    registry.getAnnotations(classOf[Test], "channelRead") shouldBe empty

    // Does not implement outbound interface; should fail trying to resolve
    a [NoSuchMethodException] should be thrownBy registry.getAnnotations(classOf[Test], "write")
  }

  it should "find only marked annotation" in {
    abstract class Test extends ChannelInboundHandler {
      @Timed @Gauge @Metered
      override def channelRegistered(ctx: ChannelHandlerContext) {}
      @Timed
      override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef) {}
    }

    // No annotations for the inbound-methods
    val registry = new MetricsAnnotationRegistry
    registry.getAnnotations(classOf[Test], "channelRegistered").size shouldBe 3
    registry.getAnnotations(classOf[Test], "channelRegistered")(0) shouldBe a [Timed]
    registry.getAnnotations(classOf[Test], "channelRegistered")(1) shouldBe a [Gauge]
    registry.getAnnotations(classOf[Test], "channelRegistered")(2) shouldBe a [Metered]
    registry.getAnnotations(classOf[Test], "channelRead").size shouldBe 1
    registry.getAnnotations(classOf[Test], "channelRead")(0) shouldBe a [Timed]
  }

  it should "skip unrelated annotations" in {
    abstract class Test extends ChannelInboundHandler {
      override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef) {}
    }

    val registry = new MetricsAnnotationRegistry
    registry.getAnnotations(classOf[Test], "channelRead") shouldBe empty
  }

  // registry cachings
  it should "cache subsequent invocations" in {
    //fail("Not implemented yet...")
  }
}