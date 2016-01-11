package com.ebay.neutrino.balancer

import com.ebay.neutrino.{NeutrinoNode, NeutrinoRequest}
import com.typesafe.scalalogging.slf4j.StrictLogging

/**
 * An algorithmically naive implementation of a least-connection operation.
 *
 * This implementation currently is O(n) to resolve 'lowest'.
 *
 * TODO Efficient Data Structure
 *  - The ideal would be some form of Red/Black tree (ie: TreeMap[Node, Int])
 *  - Unfortunately, we can only order based on Key, not Value
 *  - Recommend custom data structure based on LinkedList:
 *      - Parent structure a linked-array of "per-count buckets"  (List[(count, List[Entry[NeutrinoNode]])]
 *      - Assign comes from front of parent list (to determine lowest 'count' bucket)
 *      - Assign from bucket = first in bucket
 *        - Move to next bucket, inserting if not contiguous
 *        - On move, add to front of new/next bucket
 *        - Shift remaining items in old bucket back to root
 *      - Release from bucket is reverse
 *      - Secondary HashMap[NeutrinoNode, Entry[NeutrinoNode]] maps back to node and resolve down
 *      - *** Locking required on every operation; scalability issue
 */
class LeastConnectionBalancer extends Balancer with StrictLogging {

  private case class Entry(node: NeutrinoNode, var count: Int = 0)

  // Only requires synchronization on structural changes.
  private val members = new BalancerNodes[Entry]()

  // Re(set) the current membership of the load-balancer
  // ?? Should rebuild just do a new map replacement? Need to mark/sweep/copy ints
  override def rebuild(members: Array[NeutrinoNode]) = this.members.set(members, Entry(_))


  /**
   * Resolve an endpoint for request processing.
   *
   * @param request
   * @return
   */
  def assign(request: NeutrinoRequest): Option[NeutrinoNode] =
    if (members.isEmpty)
      None
    else {
      // INEFFICIENT = THIS NEEDS TO BE FIXED WITH INTERNAL SORTED LIST
      // SEE NOTE IN CLASS DESCRIPTION
      // NOTE: This requires either entry synchronization, or tolerance of over-assignment
      //    - Synchronization of whole data structure prevents inconsistent writes
      //    - Over-assignment would allow double assignment to the smallest resolved on
      //        concurrent access
      members.minBy(_.count) map { case (node, entry) =>
        entry.synchronized(entry.count += 1)
        node
      }
    }


  // Release an endpoint from request processing
  def release(request: NeutrinoRequest, node: NeutrinoNode) =
    members.get(node) match {
      case Some(entry) => entry.synchronized(entry.count -= 1)
      case None => logger.warn("Attempt to release a node which was not found in the servers: {}", node)
    }
}