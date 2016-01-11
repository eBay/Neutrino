package com.ebay.neutrino

import com.ebay.neutrino.channel.{NeutrinoSession, NeutrinoService}
import com.ebay.neutrino.config._
import io.netty.channel.Channel
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.{DefaultHttpRequest, HttpMethod, HttpRequest, HttpVersion}


/**
 * Created by cbrawn on 3/2/15.
 */
trait NeutrinoTestSupport extends NeutrinoSettingsSupport {

  implicit def core: NeutrinoCore


  /** Default channel creation
   */
  def channel(): Channel = new EmbeddedChannel()


  /**
   * Request creation support.
   * Where possible, we should pass in the containing core (either directly or via session).
   */
  def request(uri: String="/"): NeutrinoRequest =
    request(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri))

  def request(http: HttpRequest): NeutrinoRequest =
    request(session(), http)

  def request(session: NeutrinoSession, uri: String): NeutrinoRequest =
    request(session, new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri))

  def request(session: NeutrinoSession, http: HttpRequest): NeutrinoRequest =
    new NeutrinoRequest(session, http)


  /**
   * Session creation support.
   * Where possible, we should pass in the containing core.
   */
  def session(): NeutrinoSession =
    session(listenerSettings())

  def session(settings: NeutrinoSettings): NeutrinoSession =
    session(settings.interfaces.head)

  def session(settings: ListenerSettings): NeutrinoSession =
    NeutrinoSession(channel(), service(settings))


  /**
   * Service creation support.
   */
  def service(): NeutrinoService =
    service(listenerSettings())

  def service(settings: ListenerSettings): NeutrinoService =
    new NeutrinoService(core, settings)


  /**
   * Create NeutrinoPool components.
   */
  def neutrinoPool() =
    new NeutrinoPool(service(), VirtualPool())

  /* Shouldn't be required - just call core.update() instead
  public NeutrinoPool createPool(final String poolname, final String hostname, final int port) {
      return new NeutrinoPool(virtualPool(poolname, hostname, port), core);
  }*/


  /**
   * Create a NeutrinoPools component.
   */
  def neutrinoPools(pool: VirtualPool, addresses: Seq[CanonicalAddress]): NeutrinoPools =
    neutrinoPools(pool.copy(addresses=addresses))

  def neutrinoPools(pools: VirtualPool*): NeutrinoPools = {
    val npools = new NeutrinoPools(service)
    if (!pools.isEmpty) npools.update(pools:_*)
    npools
  }


  /**
   * Create a NeutrinoNode component.
   */
  def neutrinoNodes(): NeutrinoNodes =
    neutrinoNodes(neutrinoPool())

  def neutrinoNodes(pool: NeutrinoPool): NeutrinoNodes =
    new NeutrinoNodes(pool)



  // for reasonable ordering
  def sorted(pools: Seq[NeutrinoPool]) = pools sortBy (pool => pool.settings.id)

}


/**
 * Mock support for creating various Neutrino settings types.
 *
 * Created by cbrawn on 3/2/15.
 */
trait NeutrinoSettingsSupport {

  /**
   * Create a BalancerSettings object.
   */
  def balancerSettings(pools: VirtualPool*): LoadBalancer =
    new LoadBalancer("id", pools)


  /**
   * Create a ListenerAddress object.
   */
  def listenerAddress(host: String="localhost", port: Int=8080, transport: Transport=Transport.HTTP) =
    ListenerAddress(host, port, transport)


  /**
   * Create a CanonicalAddress object.
   * @return
   */
  def canonicalAddress(): CanonicalAddress =
    CanonicalAddress("www.ebay.com", 80, Transport.HTTP, TimeoutSettings.Default)

  def canonicalAddresses(): Seq[CanonicalAddress] =
    Seq(canonicalAddress())


  /**
   * Create a ListenerSettings object.
   */
  def listenerSettings(): ListenerSettings =
    listenerSettings(listenerAddress())

  def listenerSettings(port: Int): ListenerSettings =
    listenerSettings(listenerAddress(port=port))

  def listenerSettings(address: ListenerAddress): ListenerSettings =
    listenerSettings(address, address.protocol)

  def listenerSettings(address: ListenerAddress, transport: Transport): ListenerSettings =
    ListenerSettings(
      Seq(address), transport, Seq(address.port), Seq(), Seq(), ChannelSettings.Default, TimeoutSettings.Default
    )


  /**
   * Create a VirtualPool object.
   */
  def virtualPool(id: String="id", host: String="www.ebay.com", port: Int=80, transport: Transport=Transport.HTTP) = {
    val server  = VirtualServer(id, host, port)
    val address = CanonicalAddress(host, port, transport)
    VirtualPool(id, transport, Seq(server), Seq(address))
  }

  def virtualPool(poolname: String, servers: VirtualServer*): VirtualPool =
    VirtualPool(poolname, Transport.HTTP, servers, Seq.empty)



  /**
   * Create VirtualServer components.
   */
  def virtualServer(hostname: String, port: Int): VirtualServer =
    VirtualServer(hostname, hostname, port, None)

}
