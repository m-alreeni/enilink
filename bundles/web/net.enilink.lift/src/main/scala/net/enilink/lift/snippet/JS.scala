package net.enilink.lift.snippet

import scala.xml.NodeSeq
import net.liftweb.http.DispatchSnippet
import net.liftweb.http.LiftRules
import scala.xml.Unparsed
import net.liftweb.http.S
import net.liftweb.http.js._
import net.liftweb.http.js.JsCmds._
import net.liftweb.http.js.JE._
import net.liftweb.http.SHtml
import net.liftweb.http.AjaxContext
import net.liftweb.common.Full
import net.liftweb.http.Templates
import scala.xml.Group
import net.liftweb.http.JsonResponse
import net.liftweb.http.js.jquery.JqJE
import net.liftweb.http.JavaScriptResponse
import net.liftweb.json._
import scala.xml.Elem
import net.liftweb.http.NotFoundResponse
import java.io.ByteArrayInputStream
import net.liftweb.common.Full
import net.liftweb.common.Empty
import net.enilink.lift.util.AjaxHelpers
import net.liftweb.json.DefaultFormats
import net.liftweb.json._
import net.liftweb.util.JsonCommand

case class RenderTemplateInput(templatePath: String, templateName: Option[String], bind: Option[JObject])

/**
 * Snippets for embedding of JS scripts.
 */
object JS extends DispatchSnippet with SparqlHelper {
  def dispatch: DispatchIt = {
    case "bootstrap" => _ => bootstrap
    case "rdfa" => _ => rdfa
    case "templates" => _ => templates
  }

  private def script(src: String) = <script src={ src } type="text/javascript" data-lift="head"></script>

  def bootstrap: NodeSeq = List("bootstrap.min", "bootstrap-ext").flatMap {
    lib => script("/" + LiftRules.resourceServerPath + "/bootstrap/js/" + lib + ".js")
  }

  def rdfa: NodeSeq = script("/" + LiftRules.resourceServerPath + "/rdfa/jquery.rdfquery.rdfa.js")

  implicit val formats = DefaultFormats
  def ajax: PartialFunction[JValue, JValue] = {
    case JsonCommand("renderTemplate", _, params) =>
      val result = for (
        RenderTemplateInput(pathOrXml, templateName, bind) <- params.extractOpt[RenderTemplateInput]
      ) yield {
        import net.enilink.lift.util.TemplateHelpers._

        def renderWithParams(ns: NodeSeq) = {
          val paramMap = bind map { b => convertParams(b.values) }
          paramMap map { QueryParams.doWith(_)(render(ns)) } getOrElse render(ns)
        }

        val isXml = "\\s*<".r.findPrefixMatchOf(pathOrXml).isDefined
        (pathOrXml match {
          case xml if isXml =>
            S.htmlProperties.htmlParser(new ByteArrayInputStream(xml.getBytes("UTF-8"))) flatMap (renderWithParams(_))
          case path => {
            val pathList = path.stripPrefix("/").split("/").toList
            withAppFor(pathList) {
              find(pathList, templateName) map { ns =>
                import net.liftweb.util.Helpers._
                // add data-lift="rdfa" for RDFa processing
                if (templateName.isDefined) ns map {
                  case e: Elem if !e.attribute("data-lift").isDefined => e % ("data-lift" -> "rdfa")
                  case other => other
                }
                else ns
              } flatMap (renderWithParams(_))
            }
          }
        }) map {
          case (ns, script) => {
            import net.liftweb.util.Helpers._
            // annotate result with template path for later invocations of renderTemplate
            val nsWithPath = ns map {
              case e: Elem if !isXml => e % ("data-t-path" -> pathOrXml)
              case other => other
            }
            val w = new java.io.StringWriter
            S.htmlProperties.htmlWriter(Group(nsWithPath), w)
            val fields = List(JField("html", JString(w.toString))) ++ script.map(js => JField("script", JString(js)))
            JObject(fields)
          }
        } openOr JObject(Nil)
      }
      result getOrElse JObject(Nil)
  }

  def templates: NodeSeq = {
    val (call, jsCmd) = S.functionLifespan(true) {
      AjaxHelpers.createJsonFunc(getClass.getName, AjaxContext.json(Full("""function(response) {
var result = response.result;
if (result === undefined || result.html === undefined) {
    console.log("Template '" + obj.params.templatePath + "' not found or execution failed.");
    return;
}

var runScript = true;
if (typeof callback === "function") {
    runScript = callback(result.html, response.script);
} else {
    $(callback).html(result.html);
}
if ((runScript === undefined || runScript) && response.script) {
    eval(response.script);
}
}""")), this.ajax)
    }

    Script(jsCmd &
      SetExp(JsVar("enilink"), Call("$.extend", JsRaw("window.enilink || {}"), //
        JsObj(
          ("renderTemplate", AnonFunc("pathOrXml, httpParams, target",
            call("renderTemplate", JsRaw("(typeof pathOrXml === 'object') ? pathOrXml : { 'templatePath' : pathOrXml }"),
              JsVar("target"), JsVar("httpParams"))) // 
              ) //
              ))))
  }
}