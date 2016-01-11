package com.ebay.neutrino.util

import java.net.InetAddress

/**
 * Created by blpaul on 12/1/2015.
 */
object ServerContext {

  val fullHostName = InetAddress.getLocalHost.getHostName

  val canonicalHostName = InetAddress.getLocalHost.getCanonicalHostName

  val hostAddress = InetAddress.getLocalHost.getHostAddress

}
