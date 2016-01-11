package com.ebay.neutrino

import com.ebay.neutrino.balancer.CNameResolver
import com.ebay.neutrino.config._
import org.scalatest.{FlatSpec, Matchers}


class PoolResolverTest extends FlatSpec with Matchers with NeutrinoTestSupport {
  behavior of "Creating Pool Resolvers"
  implicit val core = new NeutrinoCore(NeutrinoSettings.Empty)


  def createAddresses(): Seq[CanonicalAddress] = {
    Seq(CanonicalAddress("www.ebay.com", 80))
  }


  it should "create static resolvers using factor" in {
    // None
    PoolResolver("") should be (NoResolver)
    PoolResolver("none") should be (NoResolver)
    PoolResolver("NoNe") should be (NoResolver)

    // Default; static
    PoolResolver("default") should be (DefaultResolver)
    PoolResolver("defaulT") should be (DefaultResolver)

    // CNAMEs should be unique
    val cname1 = PoolResolver("cname")
    val cname2 = PoolResolver("cname")
    cname1 shouldBe a [CNameResolver]
    cname2 shouldBe a [CNameResolver]
    cname1 should not be (cname2)

    // Classname: Test valid
    val classname = classOf[TestPoolResolver].getName
    PoolResolver(classname) shouldBe a [TestPoolResolver]

    // Classname: Test invalid classname
    // This can be either ClassNotFound or NoDefFound on local for some reason
    try {
      PoolResolver(classname.toLowerCase)
    }
    catch {
      case ex: ClassNotFoundException =>
      case ex: NoClassDefFoundError =>
      case th: Throwable => fail(th)
    }

    // Classname: Should catch anythign else
    an [ClassNotFoundException] should be thrownBy PoolResolver("asdfjklab")
  }


  it should "resolve pools using 'none' resolver" in {
    val resolver = PoolResolver("none")
    val pools    = neutrinoPools()
    val passpool = VirtualPool(id="passpool1")
    val testpool = VirtualPool(id="testpool1")

    // Initial configuration; all our pools; no resolution
    pools.update(passpool, testpool)
    resolver.resolve(pools, request("/")) should be (None)

    // Same with combinations
    pools.update(passpool)
    resolver.resolve(pools, request("/")) should be (None)
  }


  it should "resolve pools using 'default' resolver" in {
    val resolver = PoolResolver("default")
    val pools    = neutrinoPools()
    val passpool = VirtualPool(id="passpool1")
    val testpool = VirtualPool(id="default")

    // Initial configuration; includes testpool
    pools.update(passpool, testpool)
    resolver.resolve(pools, request("/")) shouldBe defined
    resolver.resolve(pools, request("/")).get.settings should be (testpool)

    // Clear the valid pool from the config
    pools.update(passpool)
    resolver.resolve(pools, request("/")) should be (None)
  }


  it should "resolve pools using 'classname' resolver" in {
    val resolver = PoolResolver(classOf[TestPoolResolver].getName)
    val pools    = neutrinoPools()
    val passpool = VirtualPool(id="passpool1")
    val testpool = VirtualPool(id="testpool1")

    // Initial configuration; includes testpool
    pools.update(passpool, testpool)
    resolver.resolve(pools, request("/")) shouldBe defined
    resolver.resolve(pools, request("/")).get.settings should be (testpool)

    // Clear the valid pool from the config
    pools.update(passpool)
    resolver.resolve(pools, request("/")) should be (None)
  }
}


class NamedResolverTest extends FlatSpec with Matchers with NeutrinoTestSupport {
  behavior of "Named pool-resolver"
  implicit val core = new NeutrinoCore(NeutrinoSettings.Empty)


  it should "resolve pools using a configured named resolver" in {
    val resolver = new NamedResolver("test-name")
    val pools    = neutrinoPools()
    val passpool = VirtualPool(id="passpool1")
    val testpool = VirtualPool(id="test-name")

    // Initial configuration; includes testpool
    pools.update(passpool, testpool)
    resolver.resolve(pools, request("/")) shouldBe defined
    resolver.resolve(pools, request("/")).get.settings should be (testpool)

    // Clear the valid pool from the config
    pools.update(passpool)
    resolver.resolve(pools, request("/")) should be (None)
  }

  it should "resolve pool using static get()" in {
    val pools    = neutrinoPools()
    val poolA    = VirtualPool(id="some-pool_A")
    val poolB    = VirtualPool(id="poolb")

    // Initial configuration; includes testpool
    pools.update(poolA, poolB)

    // Check for no/match, should respect casing
    NamedResolver.get(pools, "poola") should be (None)
    NamedResolver.get(pools, "poolb").get.settings should be (poolB)
    NamedResolver.get(pools, "poolB") should be (None)
  }
}



/**
 * Test resolver; will match any pool with an ID that starts with "test..."
 */
class TestPoolResolver extends PoolResolver {

  /**
   * Attempt to resolve a pool using this request.
   */
  override def resolve(pools: NeutrinoPools, request: NeutrinoRequest): Option[NeutrinoPool] =
    pools() find (_.settings.id.startsWith("test"))
}