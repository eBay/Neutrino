package com.ebay.neutrino

import com.ebay.neutrino.balancer.{L7AddressResolver, CNameWildcardResolver, CNameResolver}
import com.typesafe.scalalogging.slf4j.StrictLogging


/**
 * A plugin interface for Pool resolution.
 *
 * This provides a way to configure default pool resolution for incoming request processing.
 *
 * Some common variants are:
 * - Default pool for interface
 * - First configured pool
 * - By request name
 */
trait PoolResolver {

  /**
   * Attempt to resolve a pool using this request.
   *
   * @param pools the source pool-set to resolve on
   * @param request the source of request
   * @return a valid pool, or None if unable to resolve
   */
  def resolve(pools: NeutrinoPools, request: NeutrinoRequest): Option[NeutrinoPool]
}


object PoolResolver {
  /**
   * Create a new PoolResolver based on the type/description provided.
   *
   * @return a valid resolver, or Unavailable if not provided
   */
  def apply(name: String): PoolResolver =
    name.toLowerCase match {
      case "none" | "" => NoResolver
      case "default" => DefaultResolver
      case "cname"   => new CNameResolver
      case "layerseven"   => new L7AddressResolver
      case classname => Class.forName(name).newInstance().asInstanceOf[PoolResolver]
    }
}


// Supported resolvers
//
object DefaultResolver extends NamedResolver("default")


/**
 * Resolve the provided pool
 */
case class StaticResolver(pool: NeutrinoPool) extends PoolResolver {
  // Attempt to resolve a pool using this request.
  override def resolve(pools: NeutrinoPools, request: NeutrinoRequest): Option[NeutrinoPool] = Option(pool)
}

/**
 * No-op pool resolver; this will never resolve a pool.
 */
object NoResolver extends PoolResolver {
  // Attempt to resolve a pool using this request.
  override def resolve(pools: NeutrinoPools, request: NeutrinoRequest): Option[NeutrinoPool] = None
}


/**
 * Resolve a pool by its ID (and transport).
 */
class NamedResolver(poolname: String) extends PoolResolver {

  // Attempt to resolve a pool using this request.
  override def resolve(pools: NeutrinoPools, request: NeutrinoRequest): Option[NeutrinoPool] =
    pools.pools get (NeutrinoPoolId(poolname, request.session))
}

object NamedResolver extends StrictLogging {

  // Retrieve the pool by name/id.
  def get(pools: NeutrinoPools, poolid: String): Option[NeutrinoPool] =
    pools() find (_.settings.id == poolid)

  // Retrieve the pool for this request, by poolid
  def get(request: NeutrinoRequest, poolid: String): Option[NeutrinoPool] =
    get(request.session.service.pools, poolid)
}
