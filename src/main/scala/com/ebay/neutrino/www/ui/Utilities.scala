package com.ebay.neutrino.www.ui

import javax.xml.transform.dom.DOMSource
import javax.xml.transform.sax.SAXResult

import scala.xml.parsing.NoBindingFactoryAdapter


/**
 * There is a long, sordid tale around pretty printing here. It all starts with
 * some useful JavaScript code that uses a character or two which are verbotten
 * in XML (ie, the && characters).
 *
 * When outputting this, we can write raw() and these will go to the output, but
 * by default when the ScalaTags 'frags' are output the default toString() method
 * simply dumps everything rendered (ie: not the raw() blocks) out as one line.
 *
 * There is PrettyPrinter support, using the default XML library.
 * > new PrettyPrinter(80,4).format(content)
 *
 * This, however, barfs on the parse of the 'invalid' characters. This means we can
 * either have:
 * - unformatted by 'normal' code (as-is)
 * - formatted code without tolerance to JavaScript un-escape
 *
 * Just escape the JavaScript, you might say. Unfortunately, this makes the browser
 * barf, as the JS is not recognized while URLEncoded.
 *
 *
 * The simplest is to do one of two things:
 * 1) Fix the parser (can't use default scala XML).
 *    Lift has one that works but has awful documentation.
 * 2) Escape the javascript with a CDATA block.
 *    Oh yes, this is also not parsed by XML properly.
 * 3) Pretty print at source.
 *    Allows us to print the first block while ignoring the JS completely.
 *    Just isn't really expected...
 *
 * So eff-it; let's just dirty up this 'pretty printer' for generating rolled-over
 * output. It's not glam but it'll keep our output trim.
 *
 * @see http://stackoverflow.com/questions/139076/how-to-pretty-print-xml-from-java
 */
object PrettyPrinter {

  val Indent = "    "


  def format(xml: String): String = {
    if (xml == null || xml.trim().length() == 0) return "";

    var indent = 0
    var inscript = false
    val pretty = new StringBuilder()
    val rows = xml.trim().replaceAll(">", ">\n").replaceAll("<", "\n<").replaceAll("\n\n", "\n").split("\n")

    // Output while adjusting row-indent
    rows.iterator foreach {
      case "" =>
        // skip

      case row if inscript =>
        pretty.append(row).append('\n')

      case row if row.startsWith("</script") =>
        inscript = false
        indent -= 1
        pretty.append(Indent*indent).append(row.trim).append('\n')

      case row if row.startsWith("</") =>
        indent -= 1
        pretty.append(Indent*indent).append(row.trim).append('\n')

    //case "<script>
      case row if (row.startsWith("<script") && !row.endsWith("/>") && !row.endsWith("]]>")) =>
        pretty.append(Indent*indent).append(row.trim).append('\n')
        indent += 1
        inscript = true

      case row if (row.startsWith("<") && !row.endsWith("/>") && !row.endsWith("]]>")) =>
        pretty.append(Indent*indent).append(row.trim).append('\n')
        indent += 1

      case row =>
        pretty.append(row).append('\n')
    }

    pretty.toString
  }

  /*def toNode(input: TypedTag[String]): xml.Node = {
    import scalatags.JsDom._
    val document = input.render
  }*/

  def toNode(input: org.w3c.dom.Node): xml.Node = {
    val source = new DOMSource(input)
    val adapter = new NoBindingFactoryAdapter
    val saxResult = new SAXResult(adapter)
    val transformerFactory = javax.xml.transform.TransformerFactory.newInstance()
    val transformer = transformerFactory.newTransformer()
    transformer.transform(source, saxResult)
    adapter.rootElem
  }
}