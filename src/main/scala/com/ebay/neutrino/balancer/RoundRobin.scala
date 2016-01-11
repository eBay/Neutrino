package com.ebay.neutrino.balancer

import com.ebay.neutrino.{NeutrinoNode, NeutrinoRequest}


class RoundRobinBalancer extends Balancer {

  type Entry = NeutrinoNode

  private val members = new BalancerNodes[Entry]()
  private var iter = members.available.iterator


  // Re(set) the current membership of the load-balancer
  override def rebuild(members: Array[NeutrinoNode]) =
    this.members.set(members, identity)


  // Resolve an endpoint for request processing
  def assign(request: NeutrinoRequest): Option[NeutrinoNode] =
    if (members.isEmpty)
      None
    else
      // Store the active iterator
      members.synchronized {
        if (iter.isEmpty) iter = members.available.iterator
        if (iter.hasNext) Option(iter.next._1)
        else None
      }

  // Release an endpoint from request processing
  def release(request: NeutrinoRequest, node: NeutrinoNode) = {}

}