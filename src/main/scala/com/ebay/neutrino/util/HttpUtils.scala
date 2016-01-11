package com.ebay.neutrino.util

import java.net.URI

import com.ebay.neutrino.config.Host
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpHeaders.{Names, Values}
import io.netty.handler.codec.http._
import io.netty.util.CharsetUtil


/**
 * A delegate implementation of the ChannelPipeline to assist with Neutrino pipeline
 * creation.
 *
 */
object HttpRequestUtils {

  import com.ebay.neutrino.util.AttributeSupport._

  import scala.collection.JavaConversions._


  // Attempt to assign the connection's pool based on the pool-name provided
  def setPool(ctx: ChannelHandlerContext, poolname: String): Boolean =
    // Try and grab the connection out of the context
    ctx.request map (_.pool.set(poolname).isDefined) getOrElse false


  // Copy any headers
  def copy(request: HttpRequest) = {
    val copy = new DefaultHttpRequest(request.protocolVersion(), request.method(), request.uri())
    request.headers() foreach { hdr => copy.headers().add(hdr.getKey, hdr.getValue) }
    copy
  }

  /**
   * Attempt to construct an absolute URI from the request, if possible, falling
   * back on relative URI
   *
   * Will take:
   * 1) HttpRequest URI, if absolute
   * 2) HttpRequest URI + Host header, if available
   * 3) HttpRequest relative URI
   */
  def URI(request: HttpRequest): URI = {

    val scheme = "http" // TODO how to extract?
    val hosthdr = Option(request.headers.get(HttpHeaderNames.HOST)) map (Host(_))

    (java.net.URI.create(request.uri), request.host(false)) match {
      case (uri, _) if uri.isAbsolute    => uri
      case (uri, None)                   => uri
      case (uri, Some(Host(host, 0)))    => new URI(scheme, uri.getAuthority, host, uri.getPort, uri.getPath, uri.getQuery, uri.getFragment)
      case (uri, Some(Host(host, port))) => new URI(scheme, uri.getAuthority, host, port, uri.getPath, uri.getQuery, uri.getFragment)
    }
  }



  // Pimp for Netty HttpRequest.
  implicit class HttpRequestSupport(val self: HttpRequest) extends AnyVal {

    /**
     * Attempt to construct an absolute URI from the request, if possible, falling
     * back on relative URI
     */
    def URI(): URI = HttpRequestUtils.URI(self)

    def host(useUri: Boolean=true): Option[Host] = useUri match {
      case true  => host(URI)
      case false => Option(self.headers.get(HttpHeaderNames.HOST)) map (Host(_))
    }

    def host(uri: URI) = {
      Option(uri.getHost) map {
        case host if uri.getPort <= 0 => Host(host.toLowerCase)
        case host => Host(host.toLowerCase, uri.getPort)
      }
    }
  }
}


/**
 * A delegate implementation of the ChannelPipeline to assist with Neutrino pipeline
 * creation.
 *
 */
object HttpResponseUtils {
  import io.netty.handler.codec.http.HttpHeaderNames._
  import io.netty.handler.codec.http.HttpVersion._


  def error(status: HttpResponseStatus) = {
    // Allocate some memory for this
    val buffer = Unpooled.copiedBuffer(s"Failure: $status\r\n", CharsetUtil.UTF_8)

    // Package a response
    val response = new DefaultFullHttpResponse(HTTP_1_1, status, buffer)
    response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8")
    response
  }
}