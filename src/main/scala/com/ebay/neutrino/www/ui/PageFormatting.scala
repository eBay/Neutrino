package com.ebay.neutrino.www.ui

import java.util.Date

import com.typesafe.scalalogging.slf4j.StrictLogging
import nl.grons.metrics.scala.{Counter, Meter}
import spray.http.ContentType
import spray.http.MediaTypes._
import spray.httpx.marshalling.Marshaller

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.xml.Elem


trait PageFormatting extends StrictLogging {

  import com.twitter.conversions.storage._
  import scala.language.implicitConversions


  val pretty    = true
  val prettyXml = false   // Use XML parsing for prettyprinting
  val prettier  = new scala.xml.PrettyPrinter(160, 4)
  val starttime = new Date()


  // Run the HTML format content through the pretty-printer
  def prettify(content: String): String = {
    if (pretty && prettyXml)
      Try(prettier.format(scala.xml.XML.loadString(content))) match {
        case Success(content) => content
        case Failure(ex) => logger.warn("Unable to pretty-print html", ex); content
      }
    else if (pretty)
      PrettyPrinter.format(content)
    else
      content
  }

  def prettyxml(content: String) = {
    Try(prettier.format(scala.xml.XML.loadString(content))) match {
      case Success(content) => content
      case Failure(ex) => logger.warn("Unable to pretty-print html", ex); content
    }
  }

  // Convert current time to uptime
  def uptime() = pretty((System.currentTimeMillis()-starttime.getTime).millis)

  // Convenience method: pretty print storage size
  def bytes(data: Long): String = data.bytes.toHuman

  // Convenience method: pretty print count size
  def count(data: Long): String =
    data match {
      case count if count < (2<<10) => s"$count"
      case count if count < (2<<18) => "%.1f K".format(count/1000f)
      case count if count < (2<<28) => "%.1f M".format(count/1000000f)
      case count                    => "%.1f G".format(count/1000000000f)
    }

  // Convenience method; pretty print time
  def pretty(duration: FiniteDuration): String = {
    if      (duration.toDays  > 0) duration.toDays+" days"
    else if (duration.toHours > 0) duration.toHours+" hours"
    else if (duration.toMinutes > 0) duration.toMinutes+" minutes"
    else     duration.toSeconds +" seconds"
  }

  // Convenience method; ensure non-null string
  @inline def str(value: String) = if (value == null) "" else value
}


object PageFormatting extends PageFormatting {
  import scalatags.Text.all._

  val SupportedOutput: Seq[ContentType] =
    Seq(`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`)


  implicit val ScalaTagsMarshaller =
    Marshaller.delegate[Frag, String](SupportedOutput:_*) { frag =>
      "<!DOCTYPE html>\n" + frag.toString
    }

  implicit val ScalaTagsPrettyMarshaller =
    Marshaller.delegate[Frag, String](SupportedOutput:_*) { frag =>
      "<!DOCTYPE html>\n" + prettyxml(frag.toString)
    }

  implicit val XmlMarshaller =
    Marshaller.delegate[Elem, String](SupportedOutput:_*) { elem =>
      prettify(elem.toString)
    }
}