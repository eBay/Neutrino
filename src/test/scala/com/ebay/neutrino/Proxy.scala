package com.ebay.neutrino

import com.ebay.neutrino.config.{Configuration, LoadBalancer}
import com.ebay.neutrino.handler.{ExampleCloseHandler, ExamplePipelineHandler}
import io.netty.channel.{Channel, ChannelInitializer}


/**
 * Create a Proxy application
 *
 * val endpoint = EndpointConfig(8081)
 * val vip      = VipSettings()
 * val server   = VirtualServer("localhost", 8081)
 *
 * // Example pluggable handler(s)
 * val pipeline = PipelineInitializer(new PipelineHandler())
 * core.initialize(core.http.service(vip, pipeline))
 * core.register(core.http.client(server))
 */
object Proxy extends App {

  // Hard-code a configuration
  // TODO move this to resource...
  val config = Configuration.load("proxy.conf", "resolvers") // dev, echo, local, algo, www, shopping

  // Create a new balancer
  val core = NeutrinoCore(config)

  // Start running. This will run until the process is interrupted...
  core.configure(LoadBalancer(config))
  core.start()
}


class ProxyPipeline extends ChannelInitializer[Channel] {

  // Initialize the user-configurable pipeline
  protected def initChannel(ch: Channel): Unit = {
    ch.pipeline.addLast(new ExampleCloseHandler())
    ch.pipeline.addLast(new ExamplePipelineHandler())
    //ch.pipeline.addLast(new ExampleCustomHandler())
  }
}