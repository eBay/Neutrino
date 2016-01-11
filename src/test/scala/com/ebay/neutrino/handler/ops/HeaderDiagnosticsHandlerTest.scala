package com.ebay.neutrino.handler.ops

import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http._
import org.scalatest.{FlatSpec, Matchers}


/**
 * Let's be perfectly clear - this unit-test exists only to bolster the code-coverage
 * of the underlying class.
 */
class HeaderDiagnosticsHandlerTest extends FlatSpec with Matchers
{
  behavior of "HeaderDiagnostics handler"

  // Create a request
  def request() = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")
  def response() = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)


  it should "work like a regular passthru handler" in {
    val marker  = new InboundMarker /// TODO duplex marker
    val channel = new EmbeddedChannel(new HeaderDiagnosticsHandler(), marker)

    // Ensure marker is empty (except for the initial registration
    marker.markerCounts should be (Map("channelActive" -> 1, "channelRegistered" -> 1))

    // Run some events through the channel and ensure they all go through
    channel.pipeline.fireChannelRegistered()
    channel.pipeline.fireChannelActive()
    channel.pipeline.fireChannelRead(request())
    channel.pipeline.fireChannelReadComplete()
    channel.pipeline.fireChannelWritabilityChanged()
    channel.pipeline.fireUserEventTriggered("userevent")
    channel.pipeline.fireChannelInactive()
    channel.pipeline.fireChannelUnregistered()
    channel.pipeline.fireExceptionCaught(new RuntimeException())
    // channel.pipeline.close() TODO
    channel.pipeline.write(response())
    // channel.pipeline.flush() TODO

    // Ensure they're all received downstream
    marker.markerCounts("channelRegistered") should be (2)
    marker.markerCounts("channelActive") should be (2)
    marker.markerCounts("channelInactive") should be (1)
    marker.markerCounts("channelUnregistered") should be (1)
    marker.markerCounts("channelRead") should be (1)
    marker.markerCounts("channelReadComplete") should be (1)
    marker.markerCounts("userEventTriggered") should be (1)
    marker.markerCounts("channelWritabilityChanged") should be (1)
    marker.markerCounts("exceptionCaught") should be (1)
  }

  // Headers only off and on




  it should "verify output format" in {
    val handler = new HeaderDiagnosticsHandler()

    // Zero handlers = Zero string
    val request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")
    handler.format(request) should be ("")

    // Add header
    request.headers().add("HEADER", "VALUE")
    handler.format(request) should not be ("")
  }
}


