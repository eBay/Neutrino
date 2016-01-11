package com.ebay.neutrino.balancer

import com.ebay.neutrino.config.{HealthState, VirtualServer}
import com.ebay.neutrino.{NeutrinoCoreSupport, NeutrinoNode}
import org.scalatest.{FlatSpec, Matchers}


class LeastConnectionTest extends FlatSpec with Matchers with NeutrinoCoreSupport {
  behavior of "Resolving Pools by LeastConnection"


  it should "simple allocation and de-allocation of one node" in {
    val request  = this.request("/")
    val balancer = new LeastConnectionBalancer

    // Check before nodes are set
    balancer.assign(request) should be (None)
    balancer.assign(request) should be (None)

    // Create one node to test against
    val pool   = neutrinoPool()
    val server = VirtualServer("id", "localhost", 8080)
    val node   = new NeutrinoNode(pool, server)

    balancer.rebuild(Array(node))
    balancer.assign(request) should be (Option(node))
    balancer.assign(request) should be (Option(node))

    // Remove the nodes and ensure none are resolved
    balancer.rebuild(Array())
    balancer.assign(request) should be (None)
    balancer.assign(request) should be (None)
  }


  it should "alternating allocation on all healthy nodes" in {
    val request  = this.request("/")
    val balancer = new LeastConnectionBalancer

    // Check before nodes are set
    balancer.assign(request) should be (None)

    // Create one node to test against
    val pool    = neutrinoPool()
    val node1   = new NeutrinoNode(pool, VirtualServer("id1", "localhost", 8080))
    val node2   = new NeutrinoNode(pool, VirtualServer("id2", "localhost", 8081))
    balancer.rebuild(Array(node1, node2))

    val assign1 = balancer.assign(request); assign1 should be (Option(node1))
    val assign2 = balancer.assign(request); assign2 should be (Option(node2))
    val assign3 = balancer.assign(request); assign3 should be (Option(node1))
    val assign4 = balancer.assign(request); assign4 should be (Option(node2))

    // Remove the nodes and ensure none are resolved
    balancer.rebuild(Array())
    balancer.assign(request) should be (None)
    balancer.assign(request) should be (None)
  }


  it should "continue allocation on original node with release" in {
    val request  = this.request("/")
    val balancer = new LeastConnectionBalancer

    // Check before nodes are set
    balancer.assign(request) should be (None)

    // Create one node to test against
    val pool    = neutrinoPool()
    val node1   = new NeutrinoNode(pool, VirtualServer("id1", "localhost", 8080))
    val node2   = new NeutrinoNode(pool, VirtualServer("id2", "localhost", 8081))
    balancer.rebuild(Array(node1, node2))

    val assign1 = balancer.assign(request); assign1 should be (Option(node1)); balancer.release(null, assign1.get)
    val assign2 = balancer.assign(request); assign2 should be (Option(node1))  // should still allocate 1
    val assign3 = balancer.assign(request); assign3 should be (Option(node2)); balancer.release(null, assign1.get)
    val assign4 = balancer.assign(request); assign4 should be (Option(node1))  // should be back at 1

    // Remove the nodes and ensure none are resolved
    balancer.rebuild(Array())
    balancer.assign(request) should be (None)
  }



  it should "round-robin allocation on all mixed-health nodes" in {
    val request  = this.request("/")
    val balancer = new LeastConnectionBalancer

    // Check before nodes are set
    balancer.assign(request) should be (None)

    // Create one node to test against
    val pool    = neutrinoPool()
    val node1   = new NeutrinoNode(pool, VirtualServer("id1", "localhost", 8081))
    val node2   = new NeutrinoNode(pool, VirtualServer("id2", "localhost", 8082))
    val node3   = new NeutrinoNode(pool, VirtualServer("id3", "localhost", 8083))
    balancer.rebuild(Array(node1, node2, node3))

    // Switch node 2 to maintenance prior to allocation
    node2.settings.healthState = HealthState.Maintenance
    val assign1 = balancer.assign(request); assign1 should be (Option(node1))
    val assign2 = balancer.assign(request); assign2 should be (Option(node3))
    val assign3 = balancer.assign(request); assign3 should be (Option(node1))
    val assign4 = balancer.assign(request); assign4 should be (Option(node3))
    balancer.release(null, assign1.get)
    balancer.release(null, assign2.get)
    balancer.release(null, assign3.get)
    balancer.release(null, assign4.get)

    // Switch node 1 and 3 off
    node1.settings.healthState = HealthState.Maintenance
    node3.settings.healthState = HealthState.Maintenance
    balancer.assign(request) should be (None)
    balancer.assign(request) should be (None)

    // Switch node 3 back on
    node3.settings.healthState = HealthState.Healthy
    balancer.assign(request) should be (Option(node3))
    balancer.assign(request) should be (Option(node3))

    // Remove the nodes and ensure none are resolved
    balancer.rebuild(Array())
    balancer.assign(request) should be (None)
  }
}