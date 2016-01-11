package com.ebay.neutrino.handler.ops

import java.nio.channels.ClosedChannelException

import com.ebay.neutrino.config.TimeoutSettings
import com.ebay.neutrino.{NettyClientSupport, NettyServerSupport}
import io.netty.channel.{Channel, ChannelHandler, ChannelInboundHandlerAdapter, ChannelInitializer}
import io.netty.handler.codec.http._
import org.scalatest.{FlatSpec, Matchers}



class ChannelTimeoutHandlerTest extends FlatSpec with Matchers with NettyClientSupport with NettyServerSupport
{
  behavior of "Channel timeout handler"

  import io.netty.handler.codec.http.HttpMethod._
  import io.netty.handler.codec.http.HttpVersion._

  import scala.concurrent.duration.Duration.Undefined
  import scala.concurrent.duration._

  // Full request/response initializer
  class TimeoutInitializer(settings: TimeoutSettings, serverHandler: ChannelHandler) extends ChannelInitializer[Channel] {
    def initChannel(channel: Channel) = {
      //Channel.pipeline.addLast(new LoggingHandler(LogLevel.INFO))
      channel.pipeline.addLast(new ChannelTimeoutHandler(settings))
      channel.pipeline.addLast(new HttpServerCodec())
      channel.pipeline.addLast(new HttpObjectAggregator(1000000))
      channel.pipeline.addLast(serverHandler)
    }
  }

  class TimeoutClient extends HttpClient {
    import com.ebay.neutrino.util.Utilities._

    override def initialzer = new HttpClientInitializer(aggregate) {
      override def initChannel(channel: Channel) = {
        super.initChannel(channel)

        // Throw a close-listener on the channel
        channel.closeFuture.addCloseListener(_ =>
          if (!response.isCompleted) response.failure(new ClosedChannelException())
        )
      }
    }
  }


  it should "handle no-timeout settings" in {
    /*
    val settings = TimeoutSettings.Undefined
    val handler  = new ChannelTimeoutHandler(settings)
    val server   = startup(handler)

    // These timers won't work I don't think...
    //val channel = new EmbeddedChannel()

    println("Shutting down")
    server.channel.close().sync()
    println("Shut down")
    */
  }


  it should "test read-idle timeout on partial request" in {
    // Server: Responds "No Timeout Received" to FullHttpRequest
    // Client: Send a partial request (indicate data pending w. content-length)
    val timeout    = 2 seconds
    val settings   = TimeoutSettings(timeout, Undefined, Undefined, Undefined, Undefined, Undefined)
    val handler    = new MessageHandler("No timeout received")
    val server     = startup(new TimeoutInitializer(settings, handler))
    val client     = new TimeoutClient()

    try {
      val request  = new DefaultHttpRequest(HTTP_1_1, POST, "/")
      HttpHeaderUtil.setContentLength(request, 100)

      val connection = client.send(request)
      connection.ready(3 seconds)

      connection.response.value shouldBe a [Some[_]]
      connection.response.value.get.isFailure should be (true)
      connection.response.value.get.failed.get shouldBe a [ClosedChannelException]
      (connection.elapsed > (timeout)) should be (true)
      (connection.elapsed < (timeout + 500.millis)) should be (true)
    }
    finally {
      // Clean up the server
      server.channel.close().sync()
    }
  }


  it should "test write-idle timeout on full request" in {
    // Server: No response to any requests
    // Client: Send a partial request (indicate data pending w. content-length)
    val timeout    = 1500 millis
    val settings   = TimeoutSettings(Undefined, timeout, Undefined, Undefined, Undefined, Undefined)
    val handler    = new ChannelInboundHandlerAdapter()
    val server     = startup(new TimeoutInitializer(settings, handler))
    val client     = new TimeoutClient()

    try {
      val request    = new DefaultHttpRequest(HTTP_1_1, GET, "/")
      val connection = client.send(request)
      connection.ready(3 seconds)

      connection.response.value shouldBe a [Some[_]]
      connection.response.value.get.isFailure should be (true)
      connection.response.value.get.failed.get shouldBe a [ClosedChannelException]
      (connection.elapsed > (timeout)) should be (true)
      (connection.elapsed < (timeout + 500.millis)) should be (true)
    }
    finally {
      // Clean up the server
      server.channel.close().sync()
    }
  }


  it should "test read-idle timeout in connect only (no request)" in {
  }

  it should "test write-idle timeout in connect only (no request)" in {
  }

  it should "test idle (both read and write specified) on min timeout on partial request" in {
  }

  it should "test write timeout with full request and no response reader (slow reader)" in {
  }

  it should "test request timeout" in {
  }

  it should "test session timeout" in {
  }
}


