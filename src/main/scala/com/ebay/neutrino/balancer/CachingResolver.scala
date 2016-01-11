package com.ebay.neutrino.balancer

import javax.annotation.Nullable

import com.ebay.neutrino.config.VirtualPool
import com.ebay.neutrino.{NeutrinoRequest, NeutrinoPools, NeutrinoPool, PoolResolver}

/**
 * Implements a simple Map-caching layer and simplified pool resolver over top of
 * the PoolResolver.
 *
 * This is useful because of the design of the resolver.
 *
 * Since NeutrinoCore and each NeutrinoPools container is immutable, it is replaced on
 * each configuration update. This container does not provide any direct mechansim to
 * optimize lookup, and so if we want to optimize type-specific operations (ie:
 * segregation by port, or lookup by host) we have to implement it externally.
 *
 * The idempotic design provides the pool by parameter, rather than through some type
 * of stateful mechanisim (ie: constructor) so we need some way to determine the
 * source pools have changed. NeutrinoPools provides this through an incremental
 * version ID.
 *
 * This class provides helper wrappers around the refresh process, insulating the
 * resolver implementation from the internals of state management.
 *
 *
 * @tparam K unique key-type for hash-map
 */
abstract class CachingResolver[K] extends PoolResolver {

  import scala.collection.JavaConversions._

  /*
  // Instrument the cache
  metrics.safegauge("cache.size")          { cnames.size }
  metrics.safegauge("cache.hitCount")      { cnames.stats.hitCount }
  metrics.safegauge("cache.missCount")     { cnames.stats.missCount }
  metrics.safegauge("cache.totalLoadTime") { cnames.stats.totalLoadTime }
  metrics.safegauge("cache.evictionCount") { cnames.stats.evictionCount }
  */

  // Cached pools; if the pools structure changes we need to pick up the changes here
  // (and flush the cache)
  private[balancer] var cached: Map[K, NeutrinoPool] = Map()

  // Our loader version; is incremented on every update
  private[balancer] var version: Int = 0



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
      cached  = rebuild(pools)
    }

    // Delegate to the one-parameter version and handle null-results
    Option(resolve(request)) flatMap (cached get(_))
  }


  protected def rebuild(pools: NeutrinoPools): Map[K, NeutrinoPool] = {
    // Generate a reference-set (configs to nodes) and rebuild our set
    val configs = pools() map (pool => pool.settings -> pool) toMap
    val rebuilt = mapAsScalaMap(rebuild(configs.keys.toIterator))
    val newpool = rebuilt mapValues (configs(_)) toMap

    newpool
  }



  /**
   * Rebuild the cached map on change of underlying source pools.
   *
   * Subclasses should implement functionality to both extract a key from the
   * pool-configuration, and filter out any non-matching pools from the
   * resulting pool-set.
   *
   * @param pools source-set of new replacement pools
   */
  def rebuild(pools: java.util.Iterator[VirtualPool]): java.util.Map[K, VirtualPool]

  /**
   * This implements a simplified Java-style interface, which allows null-return value.
   *
   * @return a valid NeutrinoPool, or null if not resolved
   */
  @Nullable
  def resolve(request: NeutrinoRequest): K
}
