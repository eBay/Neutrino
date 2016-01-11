package com.ebay.neutrino.balancer

import com.ebay.neutrino._
import com.ebay.neutrino.config.WildcardAddress
import com.ebay.neutrino.metrics.Instrumented


/**
 * A customized PoolResolver supporting pool-selection based on the CMS
 * RoutingPolicy/RouteMap configuration.
 *
 * TODO make data-structure more efficient for lookups
 * TODO fix parse order/data-structure
 * TODO add config as ctor-parameter
 */
class L7AddressResolver extends PoolResolver with Instrumented {

  // Specify our types for clarity
  // TODO this could be a Map[String, NeutrinoPool] if we didn't care about ordering
  type RouteMap  = List[(String, NeutrinoPool)]
  type HostCache = Map[String, RouteMap]

  // Cached pools; if the pools structure changes we need to pick up the changes here
  // (and flush the cache)
  private[balancer] var cached: HostCache = Map()

  // Our loader version; is incremented on every update
  private[balancer] var version: Int = 0


  /**
   * Build a multi-map of Host => (path, pool) that we can use to resolve.
   *
   * @param pools
   * @return
   */
  protected def rebuild(pools: NeutrinoPools): HostCache =
    synchronized {
      // Generate a reference-set (configs to nodes) and rebuild our set
      val configs = pools() map (pool => pool.settings -> pool) toMap

      // Extract relevant address-configurations into tuples
      val tuples = configs flatMap { case (poolcfg, pool) =>
        poolcfg.addresses collect {
          case route: WildcardAddress if (route.host.nonEmpty && route.path.nonEmpty) =>
            // Group the tuples back out to our cache format
            (route.host.toLowerCase, route.path, pool)
        }
      } toList

      // Group tuples by host-name and aggregate the path/pool pairs
      tuples groupBy (_._1) mapValues {
        _ map { case (_, path, pool) => path -> pool }
      }
    }

  /**
   * Return the pool corresponding to the request provided, if available.
   * Extract and cache the host
   *
   * TODO build caching around underlying settings rather than host/port
   * TODO handle concurrency case to prevent rebuilding of updated version multiple times
   */
  override def resolve(pools: NeutrinoPools, request: NeutrinoRequest): Option[NeutrinoPool] = {
    // Check for version change
    if (version != pools.version) {
      // Update the version to minimize regeneration
      version = pools.version
      cached = rebuild(pools)
    }

    // Identify our 'relevant' portions of the reuqest
    val host = request.host map (_.host.toLowerCase)
    val path = request.requestUri.getPath

    // Resolve all pools that match hosts
    val routemap = host flatMap (cached get (_))

    // If found, further try and match against possible pool-prefix => COULD BE REGEXES
    routemap flatMap (_ collectFirst {
      case (prefix, pool) if path.startsWith(prefix) => pool
    })
  }
}