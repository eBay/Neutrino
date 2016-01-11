package com.ebay.neutrino.health

import com.ebay.neutrino.config.VirtualServer


/**
 * Marker interface for processing health information.
 */
trait HealthProvider {

  /**
   * Retrieve the health of the specific server.
   */
  def getHealth(server: VirtualServer)

  /**
   * Register a health notification callback for the server provided.
   */
  def registerListener(server: VirtualServer, listener: HealthListener)

  /**
   * Remove a health notification callback for the server provided.
   */
  def removeListener(server: VirtualServer)
}


/**
 * Marker interface for a health listener.
 */
trait HealthListener {

  // TODO
}