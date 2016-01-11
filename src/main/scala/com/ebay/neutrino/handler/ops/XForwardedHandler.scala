package com.ebay.neutrino.handler.ops

import java.net.{InetAddress, InetSocketAddress}

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.HttpRequest

import scala.util.Try


/**
 * Creates or updates the X-Forwarded-For HTTP header.
 * <p>
 * The general format of the header key: value is:
 * <code>X-Forwarded-For: client, proxy1, proxy2</code>
 * <p>
 * See <a href="http://en.wikipedia.org/wiki/X-Forwarded-For">X-Forwarded-For Wikipedia info</a>.
 *
 * @author <a href="mailto://dabecker@ebay.com">Dan Becker</a>
 */
@Sharable
class XForwardedHandler extends ChannelInboundHandlerAdapter {

  // Child classes should override to customize proxy-IP
  def localIp(): Option[String] = XForwardedHandler.localHost

  /**
   * Resolve the client/requestor's IP address.
   *
   * Our default implementation returns the socket/channel originator's IP address.
   * Child classes should override to customize client-IP resolution
   *
   * @param ctx
   * @param request
   * @return ip/host of client, or None if unable to resolve.
   */
  def clientIp(ctx: ChannelHandlerContext, request: HttpRequest): Option[String] =
    ctx.channel.remoteAddress match {
      case inet: InetSocketAddress => Option(inet.getAddress.getHostAddress)
      case _ => None
    }


  /**
   * Propose the following changes for
   * Equivalency with previous
      (forwarded, clientIP, localHost) match {
        case (Some(fwd), Some(ip), Some(lcl)) if fwd.contains(clientIP) => s"$fwd,$lcl"
        case (Some(fwd), Some(ip), Some(lcl)) => s"$ip,$fwd,$lcl"
        case (Some(fwd), Some(ip), None)      => s"$ip,$fwd"
        case (Some(fwd), None,     Some(lcl)) => s"$fwd,$lcl"
        case (None,      Some(ip), Some(lcl)) => s"$ip,$lcl"
        case (None,      None,     Some(lcl)) => s"$lcl"
        case (Some(fwd), _, _)                => s"$fwd"
        case _                                => null  // ""
      }
    */
  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {

    msg match {
      case request: HttpRequest =>
        val client    = clientIp(ctx, request)
        val forwarded = Option(request.headers.get(XForwardedHandler.X_FORWARDED_FOR))
        val headers   = Seq(client, forwarded, localIp).flatten.distinct.mkString(",")

        // If valid (ie: non-empty), set the header
        if (headers.nonEmpty) request.headers.set(XForwardedHandler.X_FORWARDED_FOR, headers)

      case _ =>
    }

    ctx.fireChannelRead(msg)
  }
}


object XForwardedHandler {

  // TODO externalize 'standard' headers
  val X_FORWARDED_FOR  = "X-Forwarded-For"


  lazy val localHost = Try(InetAddress.getLocalHost) map (_.getHostAddress) toOption
}