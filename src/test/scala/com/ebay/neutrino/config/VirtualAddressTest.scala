package com.ebay.neutrino.config

import org.scalatest.{FlatSpec, Matchers}


class VirtualAddressTest extends FlatSpec with Matchers {


  it should "parse IP and Host Regexes properly" in {

    // Test some IPs
    VirtualAddress.isIPValid("127.0.0.1") shouldBe true
    VirtualAddress.isIPValid("montereyjenkins.stratus.qa.ebay.com") shouldBe false


    // Test some hosts
    VirtualAddress.isHostnameValid("127.0.0.1") shouldBe true   // Technically, 127.0.0.1 is also host-valid
    VirtualAddress.isHostnameValid("montereyjenkins.stratus.qa.ebay.com") shouldBe true


    // Test inverses...
  }
}