package com.ebay.neutrino.balancer

import com.ebay.neutrino.NeutrinoNode
import com.ebay.neutrino.config.HealthState


class BalancerNodes[T] {

  import com.ebay.neutrino.balancer.BalancerNodes._


  // Only requires synchronization on structural changes.
  private var servers = Map[NeutrinoNode, T]()

  // Set the values
  def set(members: Array[NeutrinoNode], creator: NeutrinoNode => T) =
    servers = members map (node => node -> creator(node)) toMap

  // Get the node
  def get(node: NeutrinoNode): Option[T] = servers.get(node)

  // Determine if we have any downstream servers available for balancing
  def isEmpty() = servers.isEmpty

  // Return a view of available servers
  def available: Iterable[(NeutrinoNode, T)] = servers.view filter {
    case (n,e) => isAvailable(n.settings.healthState)
  }

  def available(f: T => Boolean): Iterable[(NeutrinoNode, T)] =
    available filter { case (n,e) => f(e) }

  def minBy[B](f: T => B)(implicit cmp: Ordering[B]): Option[(NeutrinoNode, T)] = available match {
    case iter if iter.isEmpty => None
    case iter => Option(iter minBy { case (n,e) => f(e) })
  }

}


object BalancerNodes {

  // Helper method; determine if this health-state is available
  @inline def isAvailable(state: HealthState): Boolean = state match {
    case HealthState.Maintenance => false
    case _ => true
  }
}