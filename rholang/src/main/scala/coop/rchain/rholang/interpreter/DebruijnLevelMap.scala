package coop.rchain.rholang.interpreter

// Parameterized over T, the kind of typing discipline we are enforcing.

// An index map is implemented as a level map that calculates the index on get.
// This way you don't have to re-number the map, you just calculate the index on
// get.
class DebruijnLevelMap[T](val next: Int,
                          val env: Map[String, (Int, T, Int, Int)],
                          val wildcardCount: Int) {
  def this() = this(0, Map[String, (Int, T, Int, Int)](), 0)

  def newBinding(binding: (String, T, Int, Int)): (DebruijnLevelMap[T], Int) =
    binding match {
      case (varName, sort, line, col) =>
        (DebruijnLevelMap[T](next + 1, env + (varName -> ((next, sort, line, col))), wildcardCount),
         next)
    }

  // Returns the new map, and the first value assigned. Given that they're assigned contiguously
  def newBindings(bindings: List[(String, T, Int, Int)]): (DebruijnLevelMap[T], Int) = {
    val newMap = (this /: bindings)((map, binding) => map.newBinding(binding)._1)
    (newMap, next)
  }

  // Returns the new map, and a list of the shadowed variables
  def merge(binders: DebruijnLevelMap[T]): (DebruijnLevelMap[T], List[(String, Int, Int)]) = {
    val finalNext          = next + binders.next
    val finalWildcardCount = wildcardCount + binders.wildcardCount
    val adjustNext         = next
    binders.env.foldLeft((this, List[(String, Int, Int)]())) {
      case ((db: DebruijnLevelMap[T], shadowed: List[(String, Int, Int)]),
            (k: String, (level: Int, varType: T @unchecked, line: Int, col: Int))) =>
        val shadowedNew = if (db.env.contains(k)) (k, line, col) :: shadowed else shadowed
        (DebruijnLevelMap(finalNext,
                          db.env + (k -> ((level + adjustNext, varType, line, col))),
                          finalWildcardCount),
         shadowedNew)
    }
  }

  // Returns the new map
  def setWildcardUsed(count: Int): DebruijnLevelMap[T] =
    DebruijnLevelMap(next, env, wildcardCount + count)

  def getBinding(varName: String): Option[T] =
    for (pair <- env.get(varName)) yield pair._2
  def getLevel(varName: String): Option[Int] =
    for (pair <- env.get(varName)) yield pair._1
  def get(varName: String): Option[(Int, T, Int, Int)] = env.get(varName)
  def isEmpty()                                        = next == 0

  def count: Int            = next + wildcardCount
  def countNoWildcards: Int = next

  override def equals(that: Any): Boolean =
    that match {
      case that: DebruijnLevelMap[T] =>
        next == that.next &&
          env == that.env &&
          wildcardCount == that.wildcardCount
      case _ => false
    }

  override def hashCode(): Int =
    (next.hashCode() * 37 + env.hashCode) * 37 + wildcardCount.hashCode
}

object DebruijnLevelMap {
  def apply[T](next: Int,
               env: Map[String, (Int, T, Int, Int)],
               wildcardCount: Int): DebruijnLevelMap[T] =
    new DebruijnLevelMap(next, env, wildcardCount)

  def apply[T](): DebruijnLevelMap[T] = new DebruijnLevelMap[T]()

  def unapply[T](db: DebruijnLevelMap[T]): Option[(Int, Map[String, (Int, T, Int, Int)], Int)] =
    Some((db.next, db.env, db.wildcardCount))
}