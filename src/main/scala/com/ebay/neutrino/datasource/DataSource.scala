package com.ebay.neutrino.datasource


import com.ebay.neutrino.config.{LoadBalancer, Configuration}
import com.typesafe.config.Config

/**
 * Created by blpaul on 2/24/2016.
 */
trait DataSource {
  // refresh the datasource
  def load() : LoadBalancer

}


class FileReader extends DataSource {

  override def load(): LoadBalancer = {

    val results = Configuration.load("/etc/neutrino/slb.conf", "resolvers")
    LoadBalancer(results)

  }
}