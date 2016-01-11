package com.ebay.neutrino.balancer

import com.ebay.neutrino.config.{CanonicalAddress, Host}
import com.ebay.neutrino.metrics.Instrumented
import com.ebay.neutrino._
import com.google.common.cache.{CacheBuilder, CacheLoader}
import io.netty.handler.codec.http.HttpRequest


/**
 * A customized PoolResolver supporting an efficient CNAME-based pool selection.
 *
 * This allows a pool to be selected using the request's host value, resolving
 * a matching pool from the pool's configured CNAME and port mapping.
 *
 *
 * This implementation makes a wildcard-based match, matching the first CNAME
 * to make a postfix match. THIS MAY NOT BE WHAT YOU ARE LOOKING FOR...
 * For dynamic cases, you're probably better off using the full-match CNAME resolver.
 *
 *
 * TODO make data-structure more efficient for lookups
 * TODO fix parse order/data-structure
 * TODO add config as ctor-parameter
 */
class CNameWildcardResolver extends PoolResolver with Instrumented {
  import com.ebay.neutrino.util.HttpRequestUtils._

  // Constants   (TODO - move to settings)
  val MAX_SIZE = 1023

  // Always build the cache from the 'current' poolset.
  private[balancer] val cache = CacheBuilder.newBuilder().maximumSize(MAX_SIZE).build(new HostLoader())

  // Cached pools; if the pools structure changes we need to pick up the changes here (and flush the cache)
  private[balancer] var pools: Seq[NeutrinoPool] = Seq.empty

  // Our loader version; is incremented on every update
  private[balancer] var version = 0


  /**
   * Determine if this pool matches the request provided; find the first matching host/CNAME
   *
   * Currently, just checks for postfix match.
   * For example:
   *  host = "www.ebay.com"
   *  address.host = "ebay.com" (match)
   *  address.host = "123.www.ebay.com" (not match)
   *
   * TODO move this find out to caller to cache host
   * TODO add port support
   * TODO profile this; might benefit from an outer toIterator()
   */
  class HostLoader extends CacheLoader[Host, Option[NeutrinoPool]] {
    override def load(host: Host) = {
      val hostname  = host.host
      val hostpairs = pools flatMap  (pool => pool.settings.addresses collect { case addr:CanonicalAddress => (addr.host, pool)})
      val foundpair = hostpairs.find (pair => hostname.endsWith(pair._1))

      foundpair map (_._2)
    }
  }


  // Check for a valid poolset, updating the cache as required
  // TODO what kind of locking do we want here??
  // TODO if this was a result of an async setPools, we should do refresh() instead of invalidateAll
  @inline def cachepools(newpools: NeutrinoPools) = {
    if (version != newpools.version) {
      version = newpools.version
      pools   = newpools().toSeq /*toList*/    // Not sure why we need to cache this here
      cache.invalidateAll()
    }

    cache
  }


  /**
   * Return the pool corresponding to the request provided, if available.
   * Extract and cache the host
   *
   * TODO _ support PORT -> maybe through balancerpoools?
   */
  override def resolve(pools: NeutrinoPools, request: NeutrinoRequest): Option[NeutrinoPool] = {
    val cache = cachepools(pools)
    val host  = request.host

    host flatMap (cache.get(_))
  }
}