package com.ebay.neutrino.balancer

import java.io._
import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import com.typesafe.scalalogging.slf4j.StrictLogging

/**
 * Interesting Statistics to collect
 *
 * - measure how many capacity measurements are piggybacked on data packets
 *    (ie: packets had enough room to send capacity + framing in same TCP)
 */
// @see http://man7.org/linux/man-pages/man5/proc.5.html
object CpuStatistics extends StrictLogging {

  // Data-type for samples
  case class CpuSample(user: Double, nice: Double, system: Double, idle: Double, iowait: Double, irq: Double, softirq: Double, steal: Double, guest: Double, guest_nice: Double) {
    lazy val total = (user + nice + system + idle + iowait + irq + softirq + steal + guest + guest_nice)

    // Calculate the usage between this sample and the sample provided
    def usage(other: CpuSample) = {
      val idleDiff  = Math.abs(other.idle - idle)
      val totalDiff = Math.abs(other.total - total)

      1 - (idleDiff / totalDiff)
    }
  }

  case class UptimeSample(uptime: Double, idle: Double)

  // Constants
  val PROC_STAT   = new File("/proc/stat")    //proc_stat.txt
  val PROC_UPTIME = new File("/proc/uptime")  //proc_uptime.txt

  val ProcessorCount = Runtime.getRuntime.availableProcessors


  /**
   * Opens a stream from a existing file and return it. This style of
   * implementation does not throw Exceptions to the caller.
   *
   * @param path is a file which already exists and can be read.
   * @throws IOException
   */
  def cpuSample(): Option[CpuSample] = {
    val reader = Try(new BufferedReader(new FileReader(PROC_STAT))).toOption
    val values = reader map { _.readLine.split("\\s+").tail map (_.toDouble) }

    // Clean up our reader
    reader map { _.close() }

    values map { array =>
      val Array(user, nice, system, idle, iowait, irq, softirq, steal, guest, guest_nice) = array
      CpuSample(user, nice, system, idle, iowait, irq, softirq, steal, guest, guest_nice)
    }
  }

  def uptimeSample(): Option[UptimeSample] = {
    val reader = Try(new BufferedReader(new FileReader(PROC_UPTIME))).toOption
    val values = reader map { _.readLine.split("\\s+") map (_.toDouble) }

    // Clean up our reader
    reader map { _.close() }

    values map { array =>
      val Array(uptime, idletime) = array
      UptimeSample(uptime, idletime)
    }
  }


  /**
   * Parse /proc/stat file and fill the member values list
   *
   * Line example:
   * cpu  144025136 134535 43652864 42274006316 2718910 6408 1534597 0 131907272 0
   *
   * This implementation provided by Ashok (@asmurthy) and S.R (@raven)
   *
   * Another possible implementation can be found at:
   * @see http://stackoverflow.com/questions/1420426/calculating-cpu-usage-of-a-process-in-linux
   */
  def cpuUsage(delayInMilliseconds: Int=10): Option[Double] = {

    // TODO just use time since last sample (if ongoing)
    val sampleA = cpuSample()
    val sampleB = sampleA flatMap {_ => Thread.sleep(delayInMilliseconds); cpuSample()}

    // Calculate CPU %
    (sampleA, sampleB) match {
      case (Some(a), Some(b)) if b.total > a.total => Option(a.usage(b))
      case _ => None
    }
  }

  /**
   * Determine if the CPU measurement is available or not.
   * Naive solution - just see if a sample works.
   */
  def isCpuAvailable() = cpuUsage() isDefined

}


object CpuStatisticsApp extends App with StrictLogging {

  println (s"Processors: ${CpuStatistics.ProcessorCount}")
  println (s"CPU Usage: ${CpuStatistics.cpuUsage()}")
}