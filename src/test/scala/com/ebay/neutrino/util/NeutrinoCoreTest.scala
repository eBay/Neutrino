package com.ebay.neutrino.util

import org.scalatest.{FlatSpec, Matchers}


class DifferentialStateTest extends FlatSpec with Matchers {

  // Resort the state (for comparison)
  def sorted(state: DifferentialState[Int]): DifferentialState[Int] =
    DifferentialState(state.added.sorted, state.removed.sorted, state.updated.sorted)


  it should "test diff generation with pre/post variables" in {
    // Note - does not preserve ordering...
    {
      // Simple input lists
      val pre  = Seq(1,2,3)
      val post = Seq(2,4,6)
      sorted(DifferentialState(pre, post)) should be (DifferentialState(Seq(4,6), Seq(1,3), Seq(2)))
    }
    {
      // All added
      val pre  = Seq()
      val post = Seq(2,4,6)
      sorted(DifferentialState(pre, post)) should be (DifferentialState(Seq(2,4,6), Seq(), Seq()))
    }
    {
      // All removed
      val pre  = Seq(1,2,3)
      val post = Seq()
      sorted(DifferentialState(pre, post)) should be (DifferentialState(Seq(), Seq(1,2,3), Seq()))
    }
    {
      // All same
      val pre  = Seq(1,2,3)
      val post = Seq(1,2,3)
      sorted(DifferentialState(pre, post)) should be (DifferentialState(Seq(), Seq(), Seq(1,2,3)))
    }
  }

  // Test empty-pre/empty-post
}


class DifferentialStateSupportTest extends FlatSpec with Matchers {

  class Tester[T] extends DifferentialStateSupport[String,T] {

    var added   = Seq.empty[T]
    var removed = Seq.empty[T]
    var updated = Seq.empty[(T,T)]

    // Required methods
    def key(v: T): String = v.toString.toLowerCase
    def addState(t: T) = added = added :+ t
    def removeState(t: T) = removed = removed :+ t
    def updateState(pre: T, post: T) = updated = updated :+ (pre,post)

    def reset() = {
      added   = Seq.empty[T]
      removed = Seq.empty[T]
      updated = Seq.empty[(T,T)]
    }
  }


  // Test diffs
  it should "test diff generation" in {

    val tester = new Tester[Int]

    // Initial update
    tester.update(1,2,3,4)
    tester.added.sorted should be (Seq(1,2,3,4))
    tester.removed.sorted should be (Seq())
    tester.updated.sorted should be (Seq())
    tester.reset()

    // Second update
    tester.update(7,6,3,1)
    tester.added.sorted should be (Seq(6,7))
    tester.removed.sorted should be (Seq(2,4))
    tester.updated.sorted should be (Seq())   // Values are the same; no update
    tester.reset()

    // Third update
    tester.update(1,1,1,1)   // Note extras will be uniquely condensed by k
    tester.added.sorted should be (Seq())
    tester.removed.sorted should be (Seq(3,6,7))
    tester.updated.sorted should be (Seq())   // Values are the same; no update
    tester.reset()

    // Final update
    tester.update()
    tester.added.sorted should be (Seq())
    tester.removed.sorted should be (Seq(1))
    tester.updated.sorted should be (Seq())
    tester.reset()
  }

  // Test version TODO


  it should "test update generation" in {

    val tester = new Tester[String]

    // Initial setup
    tester.update("A","B","C")
    tester.added.sorted should be (Seq("A","B","C"))
    tester.removed.sorted should be (Seq())
    tester.updated.sorted should be (Seq())
    tester.reset()

    // Update, dropping and updating A to a (same key, different value)
    tester.update("a","C")
    tester.added.sorted should be (Seq())
    tester.removed.sorted should be (Seq("B"))
    tester.updated.sorted should be (Seq(("A","a")))
    tester.reset()

  }

}