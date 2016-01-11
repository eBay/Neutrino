package com.ebay.neutrino.util

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.handler.codec.http._

import scala.xml.Elem


/**
 * Convenience methods for generating HTTP response codes.
 * TODO - build a simple cache around this...
 */
class ResponseGenerator {

  import com.ebay.neutrino.util.ResponseUtil._
  import scalatags.Text.all._


  // Cached messages
  val messages: Map[HttpResponseStatus, String] =
    Map(
      HttpResponseStatus.BAD_GATEWAY ->
        """Our apologies for the temporary inconvenience. The requested URL generated 503 "Service Unavailable" error.
          |This could be due to no available downstream servers, or overloading or maintenance of the downstream server.""".stripMargin,
      HttpResponseStatus.GATEWAY_TIMEOUT ->
        """The server was acting as a gateway or proxy and did not receive a timely response from the upstream server.."""
    )

  val DefaultMessage = messages(HttpResponseStatus.BAD_GATEWAY)


  /**
   * Generate a message, defaulting to the HttpStatus's stock message.
   */
  def generate(status: HttpResponseStatus, detail: String): FullHttpResponse =
    generate(status, messages get(status) getOrElse DefaultMessage, detail)

  /**
   * Generate a message, with the message and detail provided.
   */
  def generate(status: HttpResponseStatus, message: String, detail: String): FullHttpResponse =
    ResponseUtil.generate(status,
      html(
        head(title := s"Error ${status.code} ${status.reasonPhrase}"),
        body(
          h1(s"${status.code} ${status.reasonPhrase}"),
          s"The server reported: $message",
          h2("Additional Details:"),
          detail
        )
      ))
}


/**
 * Convenience methods for generating an HTTP response.
 *
 */
object ResponseUtil {

  import scalatags.Text

  // Constants
  val TextPlain = "text/plain; charset=UTF-8"
  val TextHtml  = "text/html; charset=UTF-8"

  // Helper methods
  private def formatter() = new scala.xml.PrettyPrinter(80, 4)


  // Generate a response from the params provided.
  def generate(status: HttpResponseStatus, content: Text.TypedTag[String]): FullHttpResponse =
    generate(status, scala.xml.XML.loadString(content.toString()))

  def generate(status: HttpResponseStatus, content: Elem): FullHttpResponse =
    generate(status, formatter().format(content)+"\r\n")

  def generate(status: HttpResponseStatus, content: String): FullHttpResponse =
    generate(status, Unpooled.wrappedBuffer(content.getBytes), TextHtml)

  def generate(status: HttpResponseStatus, buffer: ByteBuf): FullHttpResponse =
    generate(status, buffer, TextPlain)

  def generate(status: HttpResponseStatus, buffer: ByteBuf, contentType: String): FullHttpResponse =
    generate(status, buffer, contentType, HttpVersion.HTTP_1_1)

  def generate(status: HttpResponseStatus, buffer: ByteBuf, contentType: String, version: HttpVersion): FullHttpResponse = {
    val response = new DefaultFullHttpResponse(version, status, buffer)
    response.headers.set(HttpHeaderNames.CONTENT_TYPE, contentType)
    response.headers.set(HttpHeaderNames.CONTENT_LENGTH, buffer.readableBytes())
    response
  }
}