package com.ebay.neutrino.channel

import com.ebay.neutrino.NeutrinoRequest


// Marker interface for Balancer lifecycle events
sealed trait NeutrinoEvent {
  def request: NeutrinoRequest
}


object NeutrinoEvent {

  // HTTP-specific
  // These are pretty simple/generic; might need to tighten these up for utility and/or brevity
  case class RequestCreated(request: NeutrinoRequest) extends NeutrinoEvent
  case class ResponseReceived(request: NeutrinoRequest) extends NeutrinoEvent
  case class ResponseCompleted(request: NeutrinoRequest) extends NeutrinoEvent
}