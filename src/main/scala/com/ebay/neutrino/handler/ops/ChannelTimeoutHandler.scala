package com.ebay.neutrino.handler.ops

import java.util.concurrent.{ConcurrentLinkedQueue, TimeUnit}

import com.ebay.neutrino.config.TimeoutSettings
import com.ebay.neutrino.metrics.Instrumented
import com.ebay.neutrino.util.Utilities
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.handler.timeout.{ReadTimeoutException, WriteTimeoutException}
import io.netty.util.concurrent.ScheduledFuture

import scala.concurrent.duration.Duration


object ChannelTimeout {

  /**
   * Timeout types.
   */
  sealed trait TimeoutType
  case object ReadIdle extends TimeoutType
  case object WriteIdle extends TimeoutType
  case object WriteIncomplete extends TimeoutType
  case object RequestMax extends TimeoutType
  case object SessionMax extends TimeoutType


  /**
   * Factor constructor.
   * Create a ChannelTimeout object if (and only if) related timeouts are defined in the settings.
   */
  def apply(ctx: ChannelHandlerContext, settings: TimeoutSettings): Option[ChannelTimeout] = {
    val min = Seq(settings.readIdle, settings.writeIdle, settings.writeCompletion, settings.requestCompletion).min

    if (min < Duration.Undefined)
      Option(new ChannelTimeout(ctx, settings))
    else
      None
  }

}


/**
 * Generic timeout task for supporting read, write, or combined idle timeouts, and a modified
 * write-completion timeout.
 *
 * Idle timeouts:
 * - simply poll at a minimal interval and check on each interval to see if our time
 *   elapsed has been exceeded.
 *
 * Write completion:
 * - Different than the existing Netty write-completion timeout
 * - Traditionally, would be implemented with a one-task per write; each write would schedule
 *   a timeout callback on itself, and then check to see if it had been cancelled (by a
 *   subsequent write completion)
 * - This incurs a very large cost in terms of GC pressure, as each task and task-schedule
 *   both create objects
 * - Instead, treat in the same way as the idle-timeout
 * - Store 'pending' timeouts in a queue, and pop them as writes complete (we assume in-order
 *   completion)
 * - On scheduled timeout trigger, we check to see if the oldest pending k has exceeded
 *   our maximum write threshold.
 *
 * @param ctx
 */
class ChannelTimeout(ctx: ChannelHandlerContext, settings: TimeoutSettings)
  extends Runnable
  with ChannelFutureListener
  with StrictLogging
{
  import ChannelTimeout._
  import com.ebay.neutrino.util.AttributeSupport._

  import scala.concurrent.duration.Duration.Undefined
  import scala.concurrent.duration._

  val maxDelay = 2500 millis

  // Track our pending writes, ordered by write-time
  val pendingWrites = new ConcurrentLinkedQueue[Long]()

  // Track our pending timeout (to allow cancellation)
  @volatile var pendingTask: ScheduledFuture[_] = null
  @volatile var lastRead  = System.nanoTime
  @volatile var lastWrite = System.nanoTime


  // Start schedule immediate
  schedule(defaultDelay)

  def schedule(delay: Duration) = {
    require(pendingTask == null, "task should not be pending")
    if (delay.isFinite) pendingTask = ctx.executor.schedule(this, delay.toNanos, TimeUnit.NANOSECONDS)
  }

  @inline def elapsed(eventTime: Long) =
    (System.nanoTime - eventTime) nanoseconds

  def sinceLast =
    elapsed(Math.max(lastRead, lastWrite))

  def nextDelay =
    Seq(readDelay, writeDelay, requestDelay).min

  def defaultDelay =
    Seq(settings.readIdle, settings.writeIdle, settings.requestCompletion, settings.sessionCompletion, maxDelay).min

  def readDelay =
    settings.readIdle-elapsed(lastRead)

  def writeDelay =
    settings.writeIdle-elapsed(lastWrite)

  def requestDelay =
    settings.requestCompletion-(ctx.request map (_.elapsed) getOrElse Undefined)

  def completionDelay = pendingWrites.isEmpty match {
    case true  => Duration.Undefined
    case false => settings.writeCompletion-elapsed(pendingWrites.peek())
  }

  /**
   * Write-completion notification.
   *
   * On write-completion:
   * - record our last-write time
   * - remove a pending write-completion (the oldest)
   */
  override def operationComplete(future: ChannelFuture) = {
    lastWrite = System.nanoTime
    require(pendingWrites.poll().asInstanceOf[Any] != null, "expected a write pending")
  }


  // TODO if session is valid, smaller timeout, otherwise bigger timeout
  override def run(): Unit =
    if (ctx.channel.isOpen) {
      // Cache our delay
      val (read, write, request, completion) = (readDelay, writeDelay, requestDelay, completionDelay)
      val next = Seq(read, write, request, completion).min

      // Clear our pending task
      pendingTask = null

      logger.debug("Executing a timeout task with delays ({}, {}, {}, {}), {} pending writes.",
        read, write, request, completion, pendingWrites.size().toString)

      next match {
        case delay if (delay.isFinite && delay > Duration.Zero) =>
          // Operation occurred before the timeout
          // Set a new timeout with shorter delay, OR handle idle
          schedule(delay.min(maxDelay))

          if (sinceLast >= maxDelay) {
            // Force a write-check on the channel
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE)

            import Utilities._
            logger.debug("Channel {} is idle for {}ms", ctx.channel.toStringExt, sinceLast.toMillis.toString)
          }


        case delay if (delay.isFinite) =>
          // Timed out - set a new timeout and notify the callback.
          schedule(defaultDelay)

          val timeoutType = delay match {
            case delay if (request    <= Duration.Zero) => RequestMax
            case delay if (read       <= Duration.Zero) => ReadIdle
            case delay if (write      <= Duration.Zero) => WriteIdle
            case delay if (completion <= Duration.Zero) => WriteIncomplete
            case _ =>
              import Utilities._
              logger.warn("Unrecognized delay {} ({}, {}, {}, {}) on channel {}",
                delay, read, write, request, completion, ctx.channel.toStringExt
              )
              ReadIdle
          }

          ctx.pipeline.fireUserEventTriggered(timeoutType)

        case _ =>
      }
    }


  /**
   * Cancel any outstanding operations and release the task.
   */
  def cancel() =
    if (pendingTask != null) {
      // Clear the pending task
      pendingTask.cancel(false)
      pendingTask = null

      // Clear any outstanding writes
      pendingWrites.clear()
    }


  /**
   * Notify the timeout-handler of a read.
   */
  def notifyRead() = lastRead = System.nanoTime

  /**
   * Notify the timeout-handler of a pending write, and indicate whether or not
   * we are interested in listening for a callback on write-completion.
   *
   * @return true if we should be registered as a write-listener, false otherwise
   */
  def notifyWrite(): Boolean = {
    // Determine our interest in write-operations
    val listen = (settings.writeCompletion.isFinite || settings.writeIdle.isFinite)

    // Register ourself as a pending write
    if (listen) pendingWrites.add(System.nanoTime())
    listen
  }
}

/**
 * An amalgamated timeout handler supporting both Netty's stock timeouts and custom timeouts:
 * - read idle:   How long since last read operation
 * - write idle:  How long since last write operation
 * - write completion:  How long until a write has been completed (ack's by other side)
 * - request completion:  How long until an HTTP Request has been completed
 * - session completion:  How long until an HTTP session has been completed
 *
 * @param settings
 */
@Sharable
class ChannelTimeoutHandler(settings: TimeoutSettings, exceptionOnTimeout: Boolean=false)
  extends ChannelDuplexHandler
  with Instrumented
  with StrictLogging
{
  import com.ebay.neutrino.handler.ops.ChannelTimeout._
  import com.ebay.neutrino.handler.ops.NeutrinoAuditHandler._
  import com.ebay.neutrino.util.AttributeSupport._

  // Context-cached
  val counter = metrics.counterset("timeout")


  /**
   * Clear any pending timeout tasks on this channel.
   * @param ctx
   */
  private def clearTimeout(ctx: ChannelHandlerContext) = {
    ctx.timeout map { timeout =>
      timeout.cancel()
      ctx.timeout = None
    }
  }


  /**
   * Register and activate the timeout on channel activation.
   *
   * There is some debate as to where this creation should actually happen.
   * Other possibilities are channelRegistered() or handlerAdded().
   * For our specific use-case, we expect this handler will be added prior to the
   * channel activation, and want it to be as close as possible to the activation.
   *
   * @param ctx
   */
  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    require(ctx.timeout.isEmpty)
    ctx.timeout = ChannelTimeout(ctx, settings)
    ctx.fireChannelActive()
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    clearTimeout(ctx)
    ctx.fireChannelInactive()
  }

  override def channelRead(ctx: ChannelHandlerContext, msg: AnyRef): Unit = {
    ctx.timeout map (_.notifyRead())
    ctx.fireChannelRead(msg)
  }

  override def write(ctx: ChannelHandlerContext, msg: AnyRef, promise: ChannelPromise): Unit =
    ctx.timeout match {
      // If write-timeouts are specified, put a completion listener to mark our write timers
      case Some(timeout) if timeout.notifyWrite() =>
        // Cancel the scheduled timeout if the flush future is complete.
        val completed = promise.unvoid()
        completed.addListener(timeout)
        ctx.write(msg, completed)

      case _ =>
        ctx.write(msg, promise)
    }

  /**
   * Is called when any supported timeout was detected.
   */
  override def userEventTriggered(ctx: ChannelHandlerContext, evt: AnyRef): Unit =
    evt match {
      case timeout: TimeoutType if ctx.timeout.isDefined =>
        // Handle timeout notification
        counter(timeout.getClass) += 1

        val cause = timeout match {
          case WriteIdle | WriteIncomplete => WriteTimeoutException.INSTANCE
          case ReadIdle  => ReadTimeoutException.INSTANCE
          case _         => ReadTimeoutException.INSTANCE
        }

        // Store the audit record on the request, if available
        ctx.channel.audit(AuditActivity.ChannelException(ctx.channel, cause))

        // If required, throw the timeout exception
        if (exceptionOnTimeout) ctx.fireExceptionCaught(cause)

        ctx.close()
        clearTimeout(ctx)

      case timeout: TimeoutType =>
        // Already closed; just ignore

      case _ =>
        ctx.fireUserEventTriggered(evt)
    }

}