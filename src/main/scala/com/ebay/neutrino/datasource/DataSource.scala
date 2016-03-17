package com.ebay.neutrino.datasource


import com.ebay.neutrino.config.Configuration
import com.typesafe.config.Config

/**
 * Created by blpaul on 2/24/2016.
 */
trait DataSource {
  // refresh the datasource
  def load() : Config

}


class FileReader extends DataSource {

  override def load(): Config = {

    val results = Configuration.load("/etc/neutrino/slb.conf", "resolvers")
    results

  }
}