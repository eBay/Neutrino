package com.ebay.neutrino.config

import com.typesafe.config.Config

/**
 * LoadBalancer ~= VIP object
 *  we depict merged with 'Listener' type from DB
 *
 *
 * Missing attributes from LoadBalancer:
 *  - id: Long    not persisted yet; will be rquired for DB backing
 *  - name: String
 *  - description: String
 *  - vip_port_id
 *  - vip_subnet_id
 *  - vip_address
 *  - tenant_id
 *  -
 */
case class LoadBalancer(id: String, pools: Seq[VirtualPool])


object LoadBalancer {
  import scala.collection.JavaConversions._

  // LoadBalancer configuration factory; create a group of VIP configurations
  def apply(config: Seq[Config]): Seq[LoadBalancer] = config map (LoadBalancer(_))


  // LoadBalancer configuration factory; create a single VIP configuration
  // TODO support for 'default'
  def apply(cfg: Config): LoadBalancer = {
    // Build out the subconfig lists, merging with defautls
    val name  = "default" //config getString "name"
    val pool  = cfg getConfig "pool"
    val pools = cfg getConfigList "pools" map (c => VirtualPool(c withFallback pool))

    // Initialize the lifecycle-inits and wrap the load-balancer
    new LoadBalancer(name, pools) with HasConfiguration { def config = cfg }
  }
}