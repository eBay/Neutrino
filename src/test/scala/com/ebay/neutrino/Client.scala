package com.ebay.neutrino

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

import com.ebay.neutrino.util.Utilities
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.bootstrap.Bootstrap
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http._

import scala.annotation.tailrec
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{Await, Future, Promise}
import scala.util.{Failure, Success}


/**
 * Create a simple Server application
 * (one that doesn't proxy to any downstream applications)
 *
 * val endpoint = EndpointConfig(8081)
 * val vip      = VipSettings()
 * val server   = VirtualServer("localhost", 8081)
 *
 * // Example pluggable handler(s)
 * val pipeline = PipelineInitializer(new PipelineHandler())
 * core.initialize(core.http.service(vip, pipeline))
 * core.register(core.http.client(server))
 */
object Client extends App {

  //val threads = 10
  val concurrency = 200
  val total = 1000

  val client = new Client
  client.run()
}



class Client extends ChannelInitializer[SocketChannel] with ChannelFutureListener {
  import Utilities._

  import scala.concurrent.ExecutionContext.Implicits.global


  val workers = new NioEventLoopGroup()

  val address = new InetSocketAddress("localhost", 8080)

  val bootstrap = new Bootstrap()
    .channel(classOf[NioSocketChannel])
    .group(workers)
    .option[java.lang.Boolean](ChannelOption.TCP_NODELAY, true)
    .option[java.lang.Integer](ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
    .handler(this)


  /**
   * Connect to the downstream.
   * @return
   */
  def connect(): Future[ClientConnection] =
    bootstrap.connect(address).future.map (_.pipeline.get(classOf[ClientConnection]))


  // Framing decoders; extract initial HTTP connect event from HTTP stream
  protected def initChannel(channel: SocketChannel) = {
    // Configure our codecs
    //    channel.pipeline.addLast(new HttpObjectAggregator())
    //    channel.closeFuture().addListener(this)
    channel.pipeline.addLast(new HttpClientCodec())
    channel.pipeline.addLast(new ClientConnection(channel))
  }

  /**
   * Customized channel-close listener for Neutrino IO channels.
   * Capture and output IO data.
   */
  override def operationComplete(future: ChannelFuture) = {
  }

  def release(channel: Channel) = {
    // TODO reuse if we can
    // Check for concurrency, total
  }


  def send(request: FullHttpRequest, connection: ClientConnection) = {
    val result = connection.send(request)

    result map { result =>
      println(s"Success:  elapsed = ${result.elapsed.toMillis}ms")
      result
    }
  }


  def run() = {
    val total       = new AtomicInteger(100)
    val concurrency = 10
    val request     = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")

    //@tailrec
    def sender(connection: ClientConnection): Unit = {
      send(request, connection) onComplete {
        case Failure(ex)     => println(s"Exception: $ex")
        case Success(result) => if (total.decrementAndGet() > 0) sender(connection)
      }
    }

    (0 until concurrency) foreach { _ =>
      connect() foreach (sender(_))
    }
  }
}

/**
 * Downstream connection initializer.
 *
// * @param node
 */
class ClientConnection(channel: Channel) extends ChannelInboundHandlerAdapter with StrictLogging
{
  // Completion promise
  private var promise: Promise[ClientResult] = null
  private var result: ClientResult = null
  private var start = System.nanoTime()

  // Complete the current request/response and notify listeners
  private def complete(): Unit = {
    require(promise != null, "request not in progress")
    require(result != null, "response required")

    val (p, r) = (promise, result)
    promise = null
    result = null
    p.complete(Success(r))
  }


  /**
   * Handle incoming messages.
   * @param ctx
   * @param msg
   */
  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef) = {
    msg match {
      case data: HttpContent =>
        // add content stats
      case data: HttpResponse =>
        require(result == null)
        result = ClientResult(start, data)
      case _ =>
        logger.warn("Unexpected message received: {}", msg)
    }

    if (msg.isInstanceOf[LastHttpContent]) {
      complete()
    }
  }


  /**
   * Send a request on the current (idle) connection.
   * @param request
   * @return
   */
  def send(request: FullHttpRequest): Future[ClientResult] = {
    require(promise == null, "Request is outstanding; can't start")

    // Create a new completion promise
    promise = Promise[ClientResult]()
    start   = System.nanoTime()
    result  = null

    // Send the request
    channel.pipeline.writeAndFlush(request)

    promise.future
  }

}


case class ClientResult(starttime: Long, response: HttpResponse) {

  val elapsed: FiniteDuration = (System.nanoTime() - starttime) nanos
}
