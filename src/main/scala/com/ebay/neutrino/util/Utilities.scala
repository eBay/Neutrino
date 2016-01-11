package com.ebay.neutrino.util

import java.net.URI
import java.util.concurrent.atomic.AtomicLong

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.{Channel, _}
import io.netty.handler.codec.http.{DefaultFullHttpResponse, HttpResponseStatus}
import io.netty.util.CharsetUtil
import io.netty.util.concurrent.{Future => NettyFuture, FutureListener}

import scala.concurrent.{Await, CancellationException, Future, Promise}
import scala.language.implicitConversions


object Random {
  import scala.concurrent.duration._
  import scala.util.{Random => ScalaRandom}


  // Return a randomized number of the duration provided, in milliseconds
  def nextMillis(duration: Duration) =
    (ScalaRandom.nextLong() % duration.toNanos) nanos


  // Return a random number uniformly distributed over the range provided
  // Note - can only do in millisecond range
  def nextUniform(min: Duration, max: Duration): Duration =
    min + nextMillis(max - min)


  // Scale our result to the correct sigma
  def nextGaussian(mean: Duration, sigma: Duration): Duration =
    mean + (sigma * ScalaRandom.nextGaussian())

}



object Utilities {

  import io.netty.util.concurrent.{Future => NettyFuture}


  implicit class ChannelContextSupport(val self: ChannelHandlerContext) extends AnyVal {
    import io.netty.handler.codec.http.HttpHeaderNames._
    import io.netty.handler.codec.http.HttpVersion._


    def sendError(status: HttpResponseStatus): ChannelFuture =
      sendError(status, s"Failure: $status")


    def sendError(status: HttpResponseStatus, message: String): ChannelFuture = {
      val buffer = Unpooled.copiedBuffer(message+"\r\n", CharsetUtil.UTF_8)
      val response = new DefaultFullHttpResponse(HTTP_1_1, status, buffer)
      response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8")

      // Close the connection as soon as the error message is sent.
      self.writeAndFlush(response)
    }


    def closeListener = new ChannelFutureListener() {
      override def operationComplete(future: ChannelFuture): Unit =
      // was able to flush out data, start to read the next chunk
        if (future.isSuccess()) {
          self.channel().read()
        } else {
          future.channel().close()
        }
    }

    def writeAndFlush(msg: AnyRef, close: Boolean) =
      if (close) self.writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE)
      else self.writeAndFlush(msg)

    def writeAndClose(msg: AnyRef) =
      self.writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE)


    // Closes the specified channel after all queued write requests are flushed.
    def closeOnFlush = self.channel.closeOnFlush
  }


  implicit class ChannelSupport(val self: Channel) extends AnyVal {

    // Connection-state listener
    def connectListener = new ChannelFutureListener() {
      override def operationComplete(future: ChannelFuture): Unit =
      // connection complete start to read first data
        if (future.isSuccess()) self.read()
        // Close the connection if the connection attempt has failed.
        else self.close()
    }

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    def closeOnFlush = if (self.isActive())
      self.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)

    /**
     * Extract context from the channel (if available)
     */
    def context(): Option[ChannelHandlerContext] = Option(self.pipeline().firstContext())

    /**
     * Readable-string helper.
     */
    def toStringExt(): String =
    {
      import AttributeSupport._

      import scala.concurrent.duration._

      val stats   = self.statistics
      val session = s"requests=${stats.requestCount.get}, elapsed=${stats.elapsed.toMillis}ms"
      val downstream = self.request flatMap (_.downstream) match {
        case Some(channel) => s"allocations=${channel.statistics.allocations.get()}, open=${(System.nanoTime-channel.statistics.startTime).nanos.toMillis}ms"
        case None => "N/A"
      }

      s"${self.toString.dropRight(1)}, $session, $downstream]"
    }
  }


  implicit class ChannelFutureSupport(val self: ChannelFuture) extends AnyVal {

    /**
     * Add a proxy-promise listener to this ChannelFuture.
     * @param promise
     * @return
     */
    def addListener(promise: ChannelPromise) =
      self.addListener(new PromiseListener(promise))


    /**
     * Add a callback listener function to this ChannelFuture.
     * @param f
     * @return
     */
    def addCloseListener(f: Channel => Unit) =
      self.addListener(new ChannelFutureListener {
        override def operationComplete(future: ChannelFuture): Unit = f(future.channel)
      })


    /**
     * Register a Scala future on this channel.
     * Note - makes no effort to ensure only one future is returned on multiple calls. TODO improve
     */
    def future(): Future[Channel] = {
      val p = Promise[Channel]()

      self.addListener(new ChannelFutureListener {
        override def operationComplete(future: ChannelFuture) =
          if (future.isSuccess) p success (future.channel)
          else if (future.isCancelled) p failure (new CancellationException)
          else p failure (future.cause)
      })

      p.future
    }
  }


  // Note: this needs to be a concrete AnyRef rather than the existential _
  // due to Java/Scala support with overriding interfaces w. generic methods.
  implicit class FutureSupport[T](val self: NettyFuture[T]) extends AnyVal {

    def future(): Future[T] = Netty.toScala(self)
  }


  class PromiseListener(promise: ChannelPromise) extends ChannelFutureListener {
    override def operationComplete(future: ChannelFuture): Unit =
      if (future.isSuccess) promise.setSuccess(future.get())
      else promise.setFailure(future.cause())
  }

  implicit class StringSupport(val self: Array[Byte]) extends AnyVal {

    def toHexString = self.map("%02X" format _).mkString
  }



  implicit class ByteBufferSupport(val self: ByteBuf) extends AnyVal {

    // Trims leading and trailing whitespace from byte-buffer and returns a slice
    // representing the properly
    def trim(start: Int=0, length: Int=self.readableBytes) = {
      var first = start
      var last = start+length-1

      // Start at leading, and advance to first non-whitespace
      while (first <= last && self.getByte(first).toChar.isWhitespace) { first += 1 }

      // Walk back from end to first non-whitespace
      while (last >= first && self.getByte(last).toChar.isWhitespace) { last -= 1 }

      // Build the final slice
      self.slice(first, last-first+1)
    }



    def skipControlCharacters: Unit =
      while (true) {
        self.readUnsignedByte.asInstanceOf[Char] match {
          case ch if Character.isISOControl(ch) =>
          case ch if Character.isWhitespace(ch) =>
          case _ =>
            // Restore positioning
            self.readerIndex(self.readerIndex - 1)
            return
        }
      }


    def preserveIndex[T](f: => T): T = {
      val startidx = self.readerIndex

      try { f }
      finally { self.readerIndex(startidx) }
    }
  }


  /**
   * URI support methods.
   *
   * Previously, we relied on Spray's case-object based implementation, but didn't didn't want to bring
   * in the Akka transient dependencies.
   */
  implicit class URISupport(val self: URI) extends AnyVal {

    def isSecure() =
      Option(self.getScheme) map (_.startsWith("https")) getOrElse false


    def validHost: String = Option(self.getHost) match {
      case Some("") | None => require(false, "Endpoint must have a valid host"); ""
      case Some(host) => host
    }

    def validPort(default: Int=80) =
      Option(self.getPort) filter (_ != 0) getOrElse default
  }


  class LazyOption[T](f: => Option[T]) {
    private var _value: Option[T] = f

    def apply() = _value
    def reset() = { val result = _value; _value = None; result }
  }

  object LazyOption {
    import scala.concurrent.duration._

    def apply[T](f: => Option[Future[T]], timeout: Duration=1.second): LazyOption[T] = {
      // NOTE!! - TRY NOT TO USE> BLOCKING>>>>>
      new LazyOption[T]({ f map (Await.result(_, timeout)) })
    }
  }


  // Not sure if this will be instantiated or not...
  implicit class WhenSupport[T <: Any](val value: T) extends AnyVal {
    def when(check: Boolean): Option[T] = if (check) Option(value) else None
    def whenNot(check: Boolean): Option[T] = if (!check) Option(value) else None
  }


  /**
   * Atomic counter DSL support
   */
  implicit class AtomicLongSupport(val self: AtomicLong) extends AnyVal {

    def +=(delta: Int)  = self.addAndGet(delta)
    def +=(delta: Long) = self.addAndGet(delta)
  }
}

/**
 * Netty-specific utility classes.
 */
object Netty {

  def toScala[T](future: NettyFuture[T]): Future[T] = {
    val p = Promise[T]()

    future.addListener(new FutureListener[T] {
      override def operationComplete(future: NettyFuture[T]): Unit = {
        if (future.isSuccess) p success future.get
        else if (future.isCancelled) p failure new CancellationException
        else p failure future.cause
      }
    })

    p.future
  }
}

object Preconditions {

  // TODO move to Utils.Preconditions
  def checkDefined[T](f: Option[T]): T = {
    require(f.isDefined)
    f.get
  }

  def checkDefined[T](f: Option[T], msg: => Any): T = {
    require(f.isDefined, msg)
    f.get
  }
}


/**
 * Lazy-object initialization support.
 *
 * Use this when we want to defer lazy creation, but also determine if it has
 * already been created.
 */
object Lazy {

  def lazily[A](f: => A): Lazy[A] = new Lazy(f)

  implicit def evalLazy[A](l: Lazy[A]): A = l()
}

class Lazy[A] private(f: => A) {

  private var option: Option[A] = None


  def apply(): A = option match {
    case Some(a) => a
    case None => val a = f; option = Some(a); a
  }

  def isEvaluated: Boolean = option.isDefined
}