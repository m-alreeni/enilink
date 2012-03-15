package net.enilink.lift.snippet

import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.util.DynamicVariable
import scala.xml.Elem
import scala.xml.NodeSeq
import scala.xml.UnprefixedAttribute

import net.enilink.komma.model.ModelUtil
import net.enilink.komma.parser.manchester.ManchesterSyntaxGenerator
import net.enilink.lift.util.Globals
import net.enilink.lift.rdfa.template.RDFaTemplates
import net.liftweb.common.Box.box2Option
import net.liftweb.common.Box
import net.liftweb.common.Empty
import net.liftweb.common.Full
import net.liftweb.http.DispatchSnippet
import net.liftweb.http.PageName
import net.liftweb.http.S
import net.liftweb.util.Helpers.strToCssBindPromoter
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.IterableConst.itNodeSeqFunc
import net.liftweb.util.ClearNodes
import net.liftweb.util.Helpers
import net.liftweb.util.HttpHelpers
import net.liftweb.util.HttpHelpers

class RdfContext(var subject: Any, var predicate: Any) {
  override def equals(that: Any): Boolean = that match {
    case other: RdfContext => subject == other.subject && predicate == other.predicate
    case _ => false
  }
  override def hashCode = (if (subject != null) subject.hashCode else 0) + (if (predicate != null) predicate.hashCode else 0)

  override def toString = {
    "(s = " + subject + ", p = " + predicate + ")"
  }
}

object CurrentContext extends DynamicVariable[Box[RdfContext]](Empty)

class Rdf extends DispatchSnippet with RDFaTemplates {
  val VarAndMethod = "([^:]+):(.*)".r

  def dispatch: DispatchIt = {
    case VarAndMethod(v, method) => CurrentContext.value match {
      case Full(c) => v match {
        case "p" => execMethod(c.predicate, method)
        case _ => ClearNodes //TODO support access to variables
      }
      case _ => ClearNodes
    }
    case method => CurrentContext.value match {
      case Full(c) => execMethod(c.subject, method)
      case _ => ClearNodes
    }
  }

  private def execMethod(target: Any, method: String) = {
    (ns: NodeSeq) =>
      ns flatMap { n =>
        val replaceAttr = S.currentAttr("to")
        // support replacement of individual attributes
        if (replaceAttr.isDefined) {
          var attributes = n.attributes
          val attrValue = n.attribute(replaceAttr.get) getOrElse NodeSeq.Empty
          val value = method match {
            case "ref" => target.toString
            case "label" => ModelUtil.getLabel(target)
            case _ => ""
          }
          // encode if attribute is used as URL
          val encode = replaceAttr.get.toLowerCase match {
            case "href" | "src" => Helpers.urlEncode _
            case _ => (v: String) => v
          }

          var newAttrValue = attrValue.text.replaceAll("\\{\\}", encode(value))

          // insert current model into the attribute
          val model = Globals.contextModel.vend
          newAttrValue = newAttrValue.replaceAll("\\{model\\}", encode(if (model != null) model.toString else ""))

          attributes = attributes.remove(replaceAttr.get)
          attributes = attributes.append(new UnprefixedAttribute(replaceAttr.get, newAttrValue, attributes))
          n.asInstanceOf[Elem].copy(attributes = attributes)
        } else {
          val selector = if (n.attributes.isEmpty) "*" else "* *"
          (method match {
            case "ref" => selector #> target.toString
            case "manchester" => selector #> new ManchesterSyntaxGenerator().generateText(target)
            case "label" => selector #> ModelUtil.getLabel(target)
            case _ => tryo(target.getClass.getMethod(method)) match {
              case Full(meth) => meth.invoke(target) match {
                case i: java.lang.Iterable[_] => selector #> i.map(withChangedContext _)
                case i: Iterable[_] => selector #> i.map(withChangedContext _)
                case null => ClearNodes
                case o @ _ => selector #> o.toString
              }
              case _ => ClearNodes
            }
          })(n)
        }
      }
  }

  def withChangedContext(s: Any)(n: NodeSeq): NodeSeq = {
    CurrentContext.withValue(Full(new RdfContext(s, null))) {
      S.session.get.processSurroundAndInclude(PageName.get, n)
    }
  }
}