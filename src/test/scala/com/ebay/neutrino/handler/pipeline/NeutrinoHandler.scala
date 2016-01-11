package com.ebay.neutrino.handler.pipeline

import io.netty.channel._


trait NeutrinoHandler extends ChannelHandler


/**
 * Create a new operational ChannelHandler adapter to provide operational delegation.
 *
 * Due to the runtime-typecheck requirements of the inbound/outbound handlers,
 * we have to create with the explicit delegate typing.
 *
 * @param delegate
 * @return
 */
/*
object NeutrinoHandler {
  import io.netty.channel.{ChannelInboundHandler => Inbound, ChannelOutboundHandler => Outbound}

  def apply(delegate: ChannelHandler): NeutrinoHandler =
    delegate match {
      case both: Inbound with Outbound => new NeutrinoDuplexHandler(both)
      case inbound: Inbound            => new NeutrinoInboundHandler(inbound)
      case outbound: Outbound          => new NeutrinoOutboundHandler(outbound)
      case other                       => new ChannelHandlerAdapter with NeutrinoHandler
    }
}*/
