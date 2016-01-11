package com.ebay.neutrino.util


import scala.collection.mutable


// Not exactly algorithmically tuned; just needed a quick hack for splitting sets
case class DifferentialState[T](added: Seq[T], removed: Seq[T], updated: Seq[T])


object DifferentialState {

  /**
   * Create a new diff-state between the before/after.
   * NOTE - does not preserve ordering
   */
  def apply[T](pre: Iterable[T], post: Iterable[T]): DifferentialState[T] = {
    // Build a differential count accumulator
    val occ = new mutable.HashMap[T, Int] { override def default(k: T) = 0 }
    for (y <- pre) occ(y) -= 1
    for (y <- post) occ(y) += 1

    val added   = Seq.empty.genericBuilder[T]
    val removed = Seq.empty.genericBuilder[T]
    val same    = Seq.empty.genericBuilder[T]

    occ foreach {
      case (k, -1) => removed += k
      case (k,  0) => same += k
      case (k,  1) => added += k
    }

    DifferentialState(added.result, removed.result, same.result)
  }
}


trait DifferentialStateSupport[K,V] {

  // Versioning support; is updated on each update() call
  private var _version = 0

  // Internal state management
  protected var state = Seq.empty[V]

  def update(values: V*) =
    this.synchronized {
      // Map the key-values and calculate the diffs
      val before  = state.foldLeft(Map[K,V]()) { (map, v) => map + (key(v) -> v) }
      val after   = values.foldLeft(Map[K,V]()) { (map, v) => map + (key(v) -> v) }
      val diff    = DifferentialState(before.keys, after.keys)

      // Extract the associated values
      val added   = diff.added   map (k => after(k))
      val removed = diff.removed map (k => before(k))
      val updated = diff.updated flatMap (k => if (before(k) != after(k)) Option((before(k), after(k))) else None)

      // Update the cached-values
      state = values

      // If changes have been made, update
      if (added.nonEmpty || removed.nonEmpty || updated.nonEmpty) {
        removed foreach (v => removeState(v))
        updated foreach (v => updateState(v._1, v._2))
        added   foreach (v => addState(v))

        // Update the version
        _version += 1
      }
    }


  // Required subclass methods.
  // These are protected and should not be called from outside this class.
  protected def key(v: V): K
  protected def addState(added: V): Unit
  protected def removeState(remove: V): Unit
  protected def updateState(pre: V, post: V): Unit

  // Export version
  def version = _version
}