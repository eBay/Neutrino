package com.ebay.neutrino.balancer

import com.ebay.neutrino.{NeutrinoTestSupport, NeutrinoCore}
import com.ebay.neutrino.config._
import org.scalatest.{FlatSpec, Matchers}


class CNameWildcardResolverTest extends FlatSpec with Matchers with NeutrinoTestSupport
{
  behavior of "Resolving Pools by wildcard CNAME"

  import com.ebay.neutrino.util.HttpRequestUtils._

  implicit val core = new NeutrinoCore(NeutrinoSettings.Empty)


  it should "replace underlying pools on new BalancerPools use" in {
    val request  = this.request("/")
    val resolver = new CNameWildcardResolver

    resolver.version should be (0)
    resolver.pools shouldBe empty

    // Force a resolve
    val pools = neutrinoPools()
    resolver.resolve(pools, request)
    resolver.version should be (0)
    sorted(resolver.pools) should be (sorted(pools().toSeq))

    // Force a resolve with a new set of backing pools
    val pools2 = neutrinoPools(VirtualPool(id="pool2"))
    resolver.resolve(pools2, request)
    resolver.version should be (1)
    sorted(resolver.pools) should be (sorted(pools2().toSeq))
  }


  it should "test balancer-pool cache refresh" in {
    val requestA = this.request("/")
    val requestB = this.request("http://localhost/")

    // Ensure our hosts are resolved/not resolved properly
    requestA.host() should be (None)
    requestB.host() should be (Some(Host("localhost")))

    val resolver = new CNameWildcardResolver
    val pools = neutrinoPools()
    resolver.cache.size() should be (0)

    // Attempt resolve without a host
    resolver.resolve(pools, requestA)
    resolver.cache.size() should be (0)

    // Attempt resolve with a host; will add the cache-miss to the cache
    resolver.resolve(pools, requestB)
    resolver.cache.size() should be (1)
    resolver.cache.get(Host("localhost")) should be (None)

    // Resolve a second time; should hit the cache
    resolver.resolve(pools, requestA)
    resolver.resolve(pools, requestB)
    resolver.cache.size() should be (1)
    resolver.cache.get(Host("localhost")) should be (None)

    // "Refresh" with a new backing BalancerPools
    pools.update()
    resolver.resolve(pools, requestA)
    resolver.cache.size() should be (1)
  }



  it should "test cache hit" in {
    val requestA = this.request("http://localhost/")
    val requestB = this.request("http://test.stratus.qa.ebay.com/")
    val requestC = this.request("http://test.qa.ebay.com/")

    // Ensure our hosts are resolved/not resolved properly
    requestA.host() should be (Some(Host("localhost")))
    requestB.host() should be (Some(Host("test.stratus.qa.ebay.com")))
    requestC.host() should be (Some(Host("test.qa.ebay.com")))

    val resolver = new CNameWildcardResolver
    val cnames = Seq(CanonicalAddress("stratus.qa.ebay.com"))
    val pool   = VirtualPool(id="cache", address=cnames)
    val pools  = neutrinoPools(pool)

    resolver.cache.size() should be (0)

    // Attempt some resolutions
    resolver.resolve(pools, requestA) should be (None)
    resolver.resolve(pools, requestB) should not be (None)
    resolver.resolve(pools, requestC) should be (None)
    resolver.cache.size() should be (3)
  }
}


