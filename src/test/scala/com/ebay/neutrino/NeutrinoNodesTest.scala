package com.ebay.neutrino

import com.ebay.neutrino.config.{NeutrinoSettings, VirtualServer}
import org.scalatest.{FlatSpec, Matchers}


class NeutrinoNodeTest extends FlatSpec with Matchers {

  it should "provide testing for NeutrinoNode update" in {
    // TODO
  }
}


class NeutrinoNodesTest extends FlatSpec with Matchers with NeutrinoTestSupport {

  implicit val core = new NeutrinoCore(NeutrinoSettings.Empty)


  def server(id: String="id", host: String="www.ebay.com", post: Int=80): VirtualServer =
    VirtualServer(id, host, post)


  it should "ensure apply() maps to underlying state" in {
    // TODO

  }


  it should "rudmintary test of neutrino-nodes wrapper" in {
    val nodes = neutrinoNodes()
    nodes() shouldBe empty

    // Add a single node
    nodes.update(server(id="1"))
    nodes().size should be (1)

    // Add two nodes
    nodes.update(server(id="1"), server(id="2"))
    nodes().size should be (2)
    nodes() map (_.settings.id.toInt) should be (Seq(1,2))

    // Add two nodes
    nodes.update(server(id="3"))
    nodes().size should be (1)
    nodes() map (_.settings.id.toInt) should be (Seq(3))

    // Remove all nodes
    nodes.update()
    nodes().size should be (0)
    nodes() map (_.settings.id.toInt) should be (Seq())
  }


  it should "test massive concurrency access for safety" in {
    // TODO ...
  }


  it should "resolve pool by name" in {
  }
}
