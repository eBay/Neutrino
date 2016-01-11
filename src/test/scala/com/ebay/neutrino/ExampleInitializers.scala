package com.ebay.neutrino

import com.ebay.neutrino.handler.ExamplePipelineHandler
import io.netty.channel._
import io.netty.handler.codec.http.{HttpClientCodec, HttpServerCodec}
import io.netty.handler.logging.LoggingHandler


object ExampleInitializers {


  class HttpFramingInitializer extends ChannelInitializer[Channel] {

    // Assume HTTP -> HTTP
    protected def initChannel(ch: Channel): Unit = {
      ch.pipeline.addFirst(new HttpServerCodec())
      ch.pipeline.addLast(new HttpClientCodec())
    }
  }

  class OperationsFramingInitializer extends ChannelInitializer[Channel] {

    // Mock with an example framing initializer
    protected def initChannel(ch: Channel): Unit = {
      ch.pipeline.addLast(new LoggingHandler())
    }
  }

  class UserPipelineInitializer extends ChannelInitializer[Channel] {

    // Mock with an example framing initializer
    protected def initChannel(ch: Channel): Unit = {
      ch.pipeline.addLast(new ExamplePipelineHandler())
    }
  }
}