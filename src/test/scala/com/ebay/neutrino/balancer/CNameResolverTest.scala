package com.ebay.neutrino.balancer

import com.ebay.neutrino.config._
import com.ebay.neutrino.{NeutrinoCore, NeutrinoTestSupport}
import org.scalatest.{FlatSpec, Matchers}


class CNameResolverTest extends FlatSpec with Matchers with NeutrinoTestSupport
{
  behavior of "Resolving Pools by CNAME"
  implicit val core = new NeutrinoCore(NeutrinoSettings.Empty)


  it should "replace underlying pools on new BalancerPools use" in {
    val settings = ListenerSettings(sourcePorts=Seq(80,443))
    val pools    = service(settings).pools
    val request  = this.request("/")
    val resolver = new CNameResolver

    resolver.cached shouldBe empty
    resolver.version should be (0)

    // Configure pools once and force a resolve
    pools.update(virtualPool("www", "www.ebay.com", 80), virtualPool("api", "api.ebay.com", 443))

    pools().size should be (2)
    resolver.resolve(pools, request)
    resolver.version should be (1)
    resolver.cached.size should be (2)
    sorted(resolver.cached.map(_._2).toList) should be (sorted(pools().toList))

    // Reconfigure and force a resolve with a new set of backing pools
    pools.update(virtualPool("paypal-1", "www.paypal.com", 8082, Transport.HTTPS))
    pools().size should be (1)
    resolver.resolve(pools, request)
    resolver.version should be (2)
    resolver.cached.size should be (0)
    resolver.cached.map(_._2) should not be (pools())

    // Now with a matching port/transport
    pools.update(virtualPool("paypal-1", "www.paypal.com", 443, Transport.HTTP))
    pools().size should be (1)
    resolver.resolve(pools, request)
    resolver.version should be (3)
    resolver.cached.size should be (1)
    resolver.cached.map(_._2) should not be (pools())
  }


  it should "test resolution by host, ignoring transport" in {
    val settings = listenerSettings(80)
    val service  = this.service(settings)
    val session  = this.session(settings)
    val requestA = this.request(session, "http://localhost/")
    val requestB = this.request(session, "http://test.stratus.qa.ebay.com/")
    val requestC = this.request(session, "http://test.qa.ebay.com/")
    val requestD = this.request(session, "http://STRATUS.qa.ebaY.com/")

    // Ensure our hosts are resolved/not resolved properly
    requestA.host should be (Some(Host("localhost")))
    requestB.host should be (Some(Host("test.stratus.qa.ebay.com")))
    requestC.host should be (Some(Host("test.qa.ebay.com")))
    requestD.host should be (Some(Host("stratus.qa.ebay.com")))

    val resolver = new CNameResolver
    val cnames = Seq(CanonicalAddress("stratus.qa.ebay.com", 80), CanonicalAddress("stratus.qa.ebay.com", 443))
    val pool   = VirtualPool(id="cache", address=cnames)
    service.pools.update(pool)

    // Attempt some resolutions
    resolver.resolve(service.pools, requestA) should be (None)
    resolver.resolve(service.pools, requestB) should be (None)
    resolver.resolve(service.pools, requestC) should be (None)
    resolver.resolve(service.pools, requestD) should not be (None)
  }


  it should "test resolution by host with explicit port, ignoring transport" in {
    val settings = listenerSettings(443)  // Unmatched default port
    val service  = this.service(settings)
    val session  = this.session()
    val requestA = this.request(session, "http://STRATUS.qa.ebaY.com/")
    val requestB = this.request(session, "http://STRATUS.qa.ebaY.com:80/")
    val requestC = this.request(session, "http://STRATUS.qa.ebaY.com:1234/")

    // Verify our hosts
    requestA.host should be (Some(Host("stratus.qa.ebay.com")))
    requestB.host should be (Some(Host("stratus.qa.ebay.com", 80)))
    requestC.host should be (Some(Host("stratus.qa.ebay.com", 1234)))

    val resolver = new CNameResolver
    val cnames = Seq(CanonicalAddress("stratus.qa.ebay.com", 443))
    val pool   = VirtualPool(id="cache", address=cnames)
    service.update(pool)

    // All should resolve; our session matches the configured addresses, regardless of request-host
    resolver.resolve(service.pools, requestA) should not be (None)
    resolver.resolve(service.pools, requestB) should not be (None)
    resolver.resolve(service.pools, requestC) should not be (None)
  }


  it should "test implicit resolution by host, ignores transport" in {
    val settings  = listenerSettings(80)
    val service   = this.service(settings)
    val resolver  = new CNameResolver
    val pool80    = virtualPool(id="cache", "stratus.qa.ebay.com", 80, Transport.HTTP)
    val pool443   = virtualPool(id="cache", "stratus.qa.ebay.com", 443, Transport.HTTPS)
    service.pools.update(pool80, pool443)

    // HTTP Session setup
    val sessionHA = this.session(listenerSettings(listenerAddress(port=80,  transport=Transport.HTTP)))
    val sessionHB = this.session(listenerSettings(listenerAddress(port=80,  transport=Transport.HTTPS)))
    val sessionSA = this.session(listenerSettings(listenerAddress(port=443, transport=Transport.HTTP)))
    val sessionSB = this.session(listenerSettings(listenerAddress(port=443, transport=Transport.HTTPS)))

    // Transports should line up
    resolver.resolve(service.pools, request(sessionHA, "http://STRATUS.qa.ebaY.com/")) should not be (None)
    resolver.resolve(service.pools, request(sessionHB, "http://STRATUS.qa.ebaY.com/")) should not be (None)
    resolver.resolve(service.pools, request(sessionSA, "http://STRATUS.qa.ebaY.com/")) should not be (None)
    resolver.resolve(service.pools, request(sessionSB, "http://STRATUS.qa.ebaY.com/")) should not be (None)
  }
}