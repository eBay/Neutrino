package com.ebay.neutrino.balancer

import com.ebay.neutrino.{NeutrinoNode, NeutrinoRequest}
import com.typesafe.scalalogging.slf4j.StrictLogging


class WeightedRoundRobinBalancer extends Balancer with StrictLogging {


  private case class Entry(node: NeutrinoNode, var load: Int = 0)

  private val members = new BalancerNodes[Entry]()


  //Re(set) the current membership of the load - balancer
  override def rebuild(members: Array[NeutrinoNode]) =
    this.members.set(members, Entry(_))


  // Resolve an endpoint for request processing
  def assign(request: NeutrinoRequest): Option[NeutrinoNode] =
    if (members.isEmpty)
      None
    else
      members.find(e => check(e)) map { case (node, entry) =>
        entry.synchronized(entry.load += 1)
        node
      }

  private def check(entry: Entry): Boolean =
    entry.node.settings.weight.get > entry.load

  // Release an endpoint from request processing
  def release(request: NeutrinoRequest, node: NeutrinoNode) =
    members.get(node) match {
      case Some(entry) => entry.synchronized(entry.load -= 1)
      case None => logger.warn("Attempt to release a node which was not found in the servers: {}", node)
    }

}