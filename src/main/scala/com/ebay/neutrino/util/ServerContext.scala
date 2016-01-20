package com.ebay.neutrino.util

import java.net.{UnknownHostException, InetAddress}

import com.typesafe.scalalogging.slf4j.StrictLogging

/**
 * Created by blpaul on 12/1/2015.
 */
object ServerContext extends StrictLogging {


  val fullHostName =  {
    var hostName : String = "Not Available"
    try {
      hostName = InetAddress.getLocalHost.getHostName
    }
    catch {
      case ex : UnknownHostException =>
        logger.warn("Unable to get the hostname")
    }
    hostName
  }

  val canonicalHostName = {
    var hostName : String = "Not Available"
    try {
      hostName = InetAddress.getLocalHost.getCanonicalHostName
    }
    catch {
      case ex : UnknownHostException =>
        logger.warn("Unable to get the hostname")
    }
    hostName
  }

  val hostAddress = {
    var hostAddress : String = "Not Available"
    try {
      hostAddress = InetAddress.getLocalHost.getHostAddress
    }
    catch {
      case ex : UnknownHostException =>
        logger.warn("Unable to get the hostaddress")
    }

    hostAddress
  }


}

