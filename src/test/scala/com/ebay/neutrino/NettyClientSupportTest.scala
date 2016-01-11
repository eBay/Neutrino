package com.ebay.neutrino

import io.netty.handler.codec.http._
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.util.Success


/**
 * Created by cbrawn on 3/3/15.
 */
class NettyClientSupportTest extends FlatSpec with Matchers with NettyClientSupport with NettyServerSupport {

  import io.netty.handler.codec.http.HttpVersion._
  import scala.concurrent.duration._


  it should "ensure client received events" in {
    val server  = startup(Seq(new MessageHandler("goodbye")))
    val client  = new HttpClient()
    val request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/")
    val conn    = client.send(request)

    Await.ready(conn.response, 2 seconds)

    conn.response.isCompleted should be (true)
    conn.response.value.get shouldBe a [Success[_]]

    server.channel.close().sync()
  }
}
