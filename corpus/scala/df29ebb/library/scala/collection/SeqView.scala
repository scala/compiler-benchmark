/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2013, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */



package scala
package collection

import generic._
import TraversableView.NoBuilder

/** A base trait for non-strict views of sequences.
 *  $seqViewInfo
 */
trait SeqView[+A, +Coll] extends SeqViewLike[A, Coll, SeqView[A, Coll]] {
  override def flatten[B](implicit asTraversable: A => GenTraversableOnce[B]): Transformed[B] = ???
  override def unzip[A1, A2](implicit asPair: A => (A1, A2)): (Transformed[A1], Transformed[A2]) = ???
  override def unzip3[A1, A2, A3](implicit asTriple: A => (A1, A2, A3)): (Transformed[A1], Transformed[A2], Transformed[A3]) = ???
}

/** An object containing the necessary implicit definitions to make
 *  `SeqView`s work. Its definitions are generally not accessed directly by clients.
 */
object SeqView {
  type Coll = TraversableView[_, C] forSome {type C <: Traversable[_]}
  implicit def canBuildFrom[A]: CanBuildFrom[Coll, A, SeqView[A, Seq[_]]] =
    new CanBuildFrom[Coll, A, SeqView[A, Seq[_]]] {
      def apply(from: Coll) = new NoBuilder
      def apply() = new NoBuilder
    }
}

