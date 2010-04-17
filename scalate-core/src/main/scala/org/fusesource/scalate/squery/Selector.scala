package org.fusesource.scalate.squery

import support.{CssParser, Combinator}
import xml.{Elem, Node}
import org.w3c.dom.Attr

/**
 * Implements CSS style selectors
 *
 * @version $Revision : 1.1 $
 */
object Selector {
  def apply(selector: String): Selector = {
    val parser = new CssParser
    parser.parseSelector(selector)
  }

  /**
   * Converts a list of selectors to a single selector on an element
   */
  def apply(selectors: Seq[Selector]): Selector = selectors match {
    case s :: Nil => s
    case s :: _ => CompositeSelector(selectors)
    case _ => AnyElementSelector()
  }

  /**
   * Converts a selector and a list of combinators into a single Selector which is capable of evaluating
   * itself from right to left on the current node
   */
  def apply(selector: Selector, combinators: Seq[Combinator]): Selector = combinators match {
  // if we had
  // a, (c1, b), (c2, c)
  // then we should create a selector
  // of c which then uses c2.selector(b, c1.selector(a))

    case Nil => selector
    case h :: Nil => h.combinatorSelector(selector)
    case h :: xs => apply(h.combinatorSelector(selector), xs)
  }

  /**
   * Returns a selector which returns the childen of the given selector
   */
  def children(selector: Selector) = ChildrenSelector(selector)


  def pseudoSelector(identifier: String): Selector = throw new IllegalArgumentException("pseudo :" + identifier + " not supported")

  def pseudoFunction(expression: AnyRef): Selector = throw new IllegalArgumentException("pseudo expression :" + expression + " not supported")

}

trait Selector {
  def matches(node: Node, parents: Seq[Node]): Boolean

  protected def attrEquals(e: Elem, name: String, value: String) = e.attribute(name) match {
    case Some(n) => n.toString == value
    case _ => false
  }
}

case class ClassSelector(className: String) extends Selector {
  def matches(node: Node, parents: Seq[Node]) = node match {
    case e: Elem =>
      attrEquals(e, "class", className)
    case _ => false
  }
}

case class IdSelector(className: String) extends Selector {
  def matches(node: Node, parents: Seq[Node]) = node match {
    case e: Elem =>
      attrEquals(e, "id", className)
    case _ => false
  }
}

case class ElementNameSelector(name: String) extends Selector {
  def matches(node: Node, parents: Seq[Node]) = node match {
    case e: Elem =>
      e.label == name
    case _ => false
  }
}

case class AttributeNameSelector(name: String) extends Selector {
  def matches(node: Node, parents: Seq[Node]) = node match {
    case e: Attr =>
      e.label == name
    case _ => false
  }
}

case class NamespacePrefixSelector(prefix: String) extends Selector {
  def matches(node: Node, parents: Seq[Node]) = node.prefix == prefix

}

case class AnyElementSelector() extends Selector {
  def matches(node: Node, parents: Seq[Node]) = node match {
    case e: Elem => true
    case _ => false
  }
}

case class CompositeSelector(selectors: Seq[Selector]) extends Selector {
  def matches(node: Node, parents: Seq[Node]) = selectors.find(!_.matches(node, parents)).isEmpty
}

case class ChildrenSelector(selector: Selector) extends Selector {
  def matches(node: Node, parents: Seq[Node]) = parents match {
    case parent :: xs =>
      selector.matches(parent, xs)
    case _ => false
  }
}

case class NotSelector(selector: Selector) extends Selector {
  def matches(node: Node, parents: Seq[Node]) = !selector.matches(node, parents)
}

object AnySelector extends Selector {
  def matches(node: Node, parents: Seq[Node]) = true
}

/**
 * Represents selector: E &gt; F
 *
 * See the <a href"http://www.w3.org/TR/css3-selectors/#child-combinators">description</a>
 */
case class ChildSelector(childSelector: Selector, parentSelector: Selector) extends Selector {
  def matches(node: Node, parents: Seq[Node]) = {
    !parents.isEmpty && childSelector.matches(node, parents) && parentSelector.matches(parents.head, parents.tail)
  }
}

/**
 * Represents selector: E F
 *
 * See the <a href"http://www.w3.org/TR/css3-selectors/#descendant-combinators">description</a>
 */
case class DescendantSelector(childSelector: Selector, parentSelector: Selector) extends Selector {
  def matches(node: Node, parents: Seq[Node]) = {
    !parents.isEmpty && childSelector.matches(node, parents) && matchParent(parents.head, parents.tail)
  }

  /**
   * recursively match the parent selector until we have no more parents
   */
  protected def matchParent(node: Node, parents: Seq[Node]): Boolean = {
    parentSelector.matches(node, parents) || (!parents.isEmpty && matchParent(parents.head, parents.tail))
  }
}

/**
 * Represents selector: E + F
 *
 * See the <a href"http://www.w3.org/TR/css3-selectors/#adjacent-sibling-combinators">description</a>
 */
case class AdjacentSiblingSelector(childSelector: Selector, parentSelector: Selector) extends Selector {
  def matches(node: Node, parents: Seq[Node]) = {
    if (!parents.isEmpty && childSelector.matches(node, parents)) {
      // lets find immediate
      // lets apply the parentSelector to the immediate parent

      // find the index of node in parents children
      val h = parents.head
      val xs = parents.tail
      val children = h.child
      val idx = children.indexOf(node)
      idx > 0 && parentSelector.matches(children(idx - 1), xs)
    }
    else {
      false
    }
  }
}

/**
 * Represents selector: E ~ F
 *
 * See the <a href"http://www.w3.org/TR/css3-selectors/#general-sibling-combinators">description</a>
 */
case class GeneralSiblingSelector(childSelector: Selector, parentSelector: Selector) extends Selector {
  def matches(node: Node, parents: Seq[Node]) = {
    if (!parents.isEmpty && childSelector.matches(node, parents)) {
      // lets find immediate
      // lets apply the parentSelector to the immediate parent

      // find the index of node in parents children
      val h = parents.head
      val xs = parents.tail

      val children = h.child
      val idx = children.indexOf(node)
      idx > 0 && children.slice(0, idx).reverse.find(parentSelector.matches(_, xs)).isDefined
    }
    else {
      false
    }
  }
}