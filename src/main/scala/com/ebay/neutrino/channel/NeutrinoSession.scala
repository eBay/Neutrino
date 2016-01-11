package com.ebay.neutrino.channel

import io.netty.channel._

/**
 * Create a new instance with the pipeline initialized with the specified handlers.
 *
 * Note initializers need to be in inner-to-outer order
 */
case class NeutrinoSession(channel: Channel, service: NeutrinoService)