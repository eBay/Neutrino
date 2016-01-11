package com.ebay.neutrino.cluster

import com.ebay.neutrino.metrics.Instrumented
import com.ebay.neutrino.{NeutrinoPoolId, NeutrinoCore}
import com.ebay.neutrino.channel.NeutrinoService
import com.ebay.neutrino.config.{LoadBalancer, CanonicalAddress, VirtualPool, Transport}
import com.ebay.neutrino.util.DifferentialStateSupport


class SLBTopology(val core: NeutrinoCore)
  extends DifferentialStateSupport[NeutrinoPoolId, VirtualPool]
  with Iterable[VirtualPool]
  with Instrumented {

  type Port = Int
  type Key  = (Port, Transport)

  protected override def key(pool: VirtualPool): NeutrinoPoolId =
    NeutrinoPoolId(pool.id.toLowerCase, pool.protocol)
  protected override def addState(added: VirtualPool): Unit = {}
  protected override def removeState(remove: VirtualPool): Unit = {}
  protected override def updateState(pre: VirtualPool, post: VirtualPool): Unit = {}

  // Iterator over available pools
  override def iterator: Iterator[VirtualPool] = state.iterator


  // Split our services into (source-port, protocol) tuples
  val services: Map[Key, NeutrinoService] =
    core.services flatMap { service =>
      val proto = service.settings.protocol
      val ports = service.settings.sourcePorts
      val pairs = ports map ((_, proto))

      // Finally, map pairs to services
      pairs map (_ -> service)
    } toMap


  // Create a gauge for each service...
  core.services map { service =>
    // Grab the port as a descriptor of the service
    val portstr = service.settings.addresses.head.port //map (_.port) mkString "_"
    val pools = service.pools

    // Create pools- and servers-gauge for the service
    metrics.gauge("pools", portstr.toString)   { pools.size }
    metrics.gauge("servers", portstr.toString) { pools.foldLeft(0) { (sum, pair) => sum + pair.servers.size }}
  }


  /**
   * Hook our parent update to delegate the update-call to the core as well.
   *
   * We currently use a naive implementation:
   *  - just find the first matching from-port/protocol
   *      (eventually, we want to be able to handle multiple matches)
   */
  override def update(values: VirtualPool*) = {
    // Update our internal state first
    super.update(values:_*)

    // Split per service and update accordingly, using configured CNAMEs
    val resolved = values flatMap { pool =>
      //
      pool.addresses collectFirst {
        case addr: CanonicalAddress if services isDefinedAt ((addr.port, pool.protocol)) =>
          services((pool.port, pool.protocol)) -> pool
      }
    }

    // Group per-service
    val grouped = resolved groupBy (_._1) mapValues (_ map (_._2))

    // Update the services directly with their relevant pools
    grouped map {
      case (service, pools) => service.update(pools:_*)
    }
  }

  def update(lb: LoadBalancer): Unit = {
    // Update the pool configuration (subclasses will cascade changes)
    update(lb.pools:_*)
  }

  def getPool(id: NeutrinoPoolId): Option[VirtualPool] =
    state collectFirst {
      case pool @ VirtualPool(id.id, _, id.transport, _, _, _, _, _) => pool }
}
