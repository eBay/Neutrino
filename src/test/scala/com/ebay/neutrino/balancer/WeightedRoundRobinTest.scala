package com.ebay.neutrino.balancer

import com.ebay.neutrino.config.{HealthState, VirtualPool, VirtualServer}
import com.ebay.neutrino.{NeutrinoNode, NeutrinoCoreSupport, NeutrinoNodes, NeutrinoRequest}
import org.scalatest.{FlatSpec, Matchers}


class WeightedRoundRobinTest extends FlatSpec with Matchers with NeutrinoCoreSupport {
  behavior of "Resolving Pools by RoundRobin"


  it should "simple allocation and de-allocation" in {
    val request = this.request("/")
    val balancer = new WeightedRoundRobinBalancer

    // Check before nodes are set
    balancer.assign(request) should be(None)
    balancer.assign(request) should be(None)

    // Create one node to test against
    val pool = neutrinoPool()
    val server = VirtualServer("id", "localhost", 8080, Some(4))
    val node = new NeutrinoNode(pool, server)

    balancer.rebuild(Array(node))
    balancer.assign(request) should be(Option(node))
    balancer.assign(request) should be(Option(node))
    balancer.assign(request) should be(Option(node))
    balancer.assign(request) should be(Option(node))
    balancer.assign(request) should be(None)

    // Remove the nodes and ensure none are resolved
    balancer.rebuild(Array())
    balancer.assign(request) should be(None)
    balancer.assign(request) should be(None)

  }


  it should "round-robin allocation on all healthy nodes" in {
    val request = this.request("/")
    val balancer = new WeightedRoundRobinBalancer

    // Check before nodes are set
    balancer.assign(request) should be(None)

    // Create one node to test against
    val pool = neutrinoPool()
    val node1 = new NeutrinoNode(pool, VirtualServer("id1", "localhost", 8080, Some(2)))
    val node2 = new NeutrinoNode(pool, VirtualServer("id2", "localhost", 8081, Some(3)))

    balancer.rebuild(Array(node1, node2))
    balancer.assign(request) should be(Option(node1))
    balancer.assign(request) should be(Option(node1))
    balancer.assign(request) should be(Option(node2))
    balancer.assign(request) should be(Option(node2))
    balancer.assign(request) should be(Option(node2))

    // Remove the nodes and ensure none are resolved
    balancer.rebuild(Array())
    balancer.assign(request) should be(None)
    balancer.assign(request) should be(None)
  }


  it should "round-robin allocation on all mixed-health nodes" in {
    val request = this.request("/")
    val balancer = new WeightedRoundRobinBalancer

    // Check before nodes are set
    balancer.assign(request) should be(None)

    // Create one node to test against
    val pool = neutrinoPool()
    val node1 = new NeutrinoNode(pool, VirtualServer("id1", "localhost", 8080, Some(2)))
    val node2 = new NeutrinoNode(pool, VirtualServer("id2", "localhost", 8081, Some(3)))

    balancer.rebuild(Array(node1, node2))
    val assign1 = balancer.assign(request); assign1 should be(Option(node1))
    val assign2 = balancer.assign(request); assign2 should be(Option(node1))
    val assign3 = balancer.assign(request); assign3 should be(Option(node2))
    val assign4 = balancer.assign(request); assign4 should be(Option(node2))
    val assign5 = balancer.assign(request); assign4 should be(Option(node2))


    balancer.release(null, assign1.get)
    balancer.release(null, assign2.get)
    balancer.release(null, assign3.get)
    balancer.release(null, assign4.get)
    balancer.release(null, assign5.get)

    // Switch node 2 to maintenance prior to allocation
    node2.settings.healthState = HealthState.Maintenance
    balancer.assign(request) should be(Option(node1))
    balancer.assign(request) should be(Option(node1))

    // Switch node 1 to maintenance prior to allocation
    node1.settings.healthState = HealthState.Maintenance
    balancer.assign(request) should be(None)

    // Switch node 2 back to OK
    node2.settings.healthState = HealthState.Healthy
    balancer.assign(request) should be(Option(node2))
    balancer.assign(request) should be(Option(node2))
    balancer.assign(request) should be(Option(node2))
    balancer.assign(request) should be(None)

    // Remove the nodes and ensure none are resolved
    balancer.rebuild(Array())
    balancer.assign(request) should be(None)
    balancer.assign(request) should be(None)
  }
}