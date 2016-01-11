package com.ebay.neutrino.api

import java.util.Date


import com.ebay.neutrino.config.CompletionStatus
import com.ebay.neutrino.metrics.Metrics
import spray.json.DefaultJsonProtocol


trait ApiData {

  import com.ebay.neutrino.api.ApiData._

  // Faked start time (should be moved to core)
  val apiStartTime = new Date()


  def generateStatus(): ApiData.Status = {
    import Metrics._
    import nl.grons.metrics.scala.{Timer => TimerData}

    def toTimer(data: TimerData) = {
      val snapshot = data.snapshot
      Timer(
        data.count,
        snapshot.getMin,
        snapshot.getMax,
        snapshot.getMean,
        snapshot.get95thPercentile,
        data.oneMinuteRate,
        data.fiveMinuteRate,
        data.fifteenMinuteRate
      )
    }


    Status(
      HostInfo(
        "localhost",
        "127.0.0.1",
        apiStartTime.toString
      ),
      Traffic(
        UpstreamBytesRead.count, UpstreamBytesWrite.count, UpstreamPacketsRead.count, UpstreamPacketsWrite.count, UpstreamOpen.count, UpstreamTotal.count
      ),
      Traffic(
        DownstreamBytesRead.count, DownstreamBytesWrite.count, DownstreamPacketsRead.count, DownstreamPacketsWrite.count, DownstreamOpen.count, DownstreamTotal.count
      ),
      Requests(     // Sessions
        SessionActive.count,
        toTimer(Metrics.SessionDuration)
      ),
      Requests(     // Requests
        RequestsOpen.count,
        toTimer(Metrics.RequestsCompleted)
      ),
      Seq(
        Responses("2xx", toTimer(Metrics.RequestsCompletedType(CompletionStatus.Status2xx))),
        Responses("4xx", toTimer(Metrics.RequestsCompletedType(CompletionStatus.Status4xx))),
        Responses("5xx", toTimer(Metrics.RequestsCompletedType(CompletionStatus.Status5xx))),
        Responses("incomplete", toTimer(Metrics.RequestsCompletedType(CompletionStatus.Incomplete))),
        Responses("other", toTimer(Metrics.RequestsCompletedType(CompletionStatus.Other)))
      )
    )
  }
}


object ApiData {

  // Supported metric types
  sealed trait MetricInt
  case class Metric(key: String, `type`: String, values: MetricInt)
  case class Counter(count: Long) extends MetricInt
  case class Timer(count: Long, min: Long, max: Long, mean: Double, `95th`: Double, `1min`: Double, `5min`: Double, `15min`: Double) extends MetricInt
  case class Meter(count: Long) extends MetricInt

  case class HostInfo(hostname: String, address: String, lastRestart: String)
  case class Traffic(bytesIn: Long, bytesOut: Long, packetsIn: Long, packetsOut: Long, currentConnections: Long, totalConnections: Long)
  //case class RequestStats(active: Long, total: Long, minElapsed: Long, avgElapsed: Long, lastRate: Long)
  case class Requests(active: Long, stats: Timer)
  case class Responses(responseType: String, stats: Timer)

  case class Status(host: HostInfo, upstreamTraffic: Traffic, downstreamTraffic: Traffic, sessions: Requests, requests: Requests, responses: Seq[Responses])



  object JsonImplicits extends DefaultJsonProtocol {
    implicit val timerJson        = jsonFormat8(Timer)
    implicit val hostinfoJson     = jsonFormat3(HostInfo)
    implicit val trafficJson      = jsonFormat6(Traffic)
    implicit val requestsJson     = jsonFormat2(Requests)
    implicit val responsesJson    = jsonFormat2(Responses)
    implicit val statusJson       = jsonFormat6(Status)
  }
}