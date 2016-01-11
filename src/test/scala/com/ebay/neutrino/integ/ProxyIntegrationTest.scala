package com.ebay.neutrino.integ

import com.ebay.neutrino.config.{Configuration, LoadBalancer, VirtualPool, VirtualServer}
import com.ebay.neutrino.handler.{ExampleCloseHandler, ExamplePipelineHandler}
import com.ebay.neutrino.{NettyClientSupport, NeutrinoCore}
import io.netty.channel.{Channel, ChannelInitializer}
import io.netty.handler.codec.http.{DefaultFullHttpRequest, HttpMethod, HttpVersion}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Ignore, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._


/**
 * Simple proxy test to a local integration server.
 * (also in netty)
 */
@Ignore
class ProxyIntegrationTest extends FlatSpec with NettyClientSupport with Matchers with BeforeAndAfterAll
{
  // Create a new balancer
  val config = Configuration.load("proxy.conf")
  val core   = NeutrinoCore(config)
  val server = new NettyEchoServer()


  override def beforeAll() = {
    val servers = Seq(VirtualServer("id", "localhost", 8081))
    val pools   = Seq(VirtualPool(servers=servers))

    // Start running the downstream server
    server.start()

    // Start running the proxy. This will run until the process is interrupted...
    core.configure(LoadBalancer("id", pools))
    Await.ready(core.start(), 5 seconds)
  }

  override def afterAll() = {
    Await.ready(core.shutdown(), 5 seconds)
    server.shutdown()
  }


  it should "run 10000 requests" in {

    // We'll have to connect as well
    val client = HttpClient(port=8080)
    val request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")

    for (i <- 0 until 10000) {
      val conn = client.send(request)
      conn.channel.close()
    }
  }

}



class ProxyIntegrationInitializer extends ChannelInitializer[Channel] {

  // Initialize the user-configurable pipeline
  protected def initChannel(ch: Channel): Unit = {
    ch.pipeline.addLast(new ExampleCloseHandler())
    ch.pipeline.addLast(new ExamplePipelineHandler())
    //pipeline.addLast(new ExampleCustomHandler())
  }
}