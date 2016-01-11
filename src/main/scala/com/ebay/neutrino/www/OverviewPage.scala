package com.ebay.neutrino.www


import com.ebay.neutrino.util.ServerContext
import com.ebay.neutrino.www.ui.PageFormatting
import com.ebay.neutrino.www.ui.Page
import com.ebay.neutrino.api.ApiData



import scala.concurrent.duration._


trait OverviewPage extends PageFormatting { self: Page =>

  import ApiData._

  import scalatags.Text.all._


  def traffic(status: Status): Frag = {
    div(cls :="table-responsive",
      table(cls :="table table-striped",
        width:="100%", "cellspacing".attr:="0", "cellpadding".attr:="0", style:="width:100%;border:1px solid;padding:4px;",

        thead(
          td(i("Last restart ", status.host.lastRestart), style:="width:25%"),
          td(b("Bytes"),       colspan:=2, textAlign:="center", style:="width:25%"),
          td(b("IO Events"),   colspan:=2, textAlign:="center", style:="width:25%"),
          td(b("Connections"), colspan:=2, textAlign:="center", style:="width:25%")
        ),
        tbody(
          tr(
            td(b("Type")),
            td(b("In")), td(b("Out")),
            td(b("In")), td(b("Out")),
            td(b("Current")), td(b("Total"))
          ),
          tr(
            td("Upstream"),
            td(bytes(status.upstreamTraffic.bytesIn)), td(bytes(status.upstreamTraffic.bytesOut)),
            td(count(status.upstreamTraffic.packetsIn)), td(count(status.upstreamTraffic.packetsOut)),
            td(status.upstreamTraffic.currentConnections), td(status.upstreamTraffic.totalConnections)
          ),
          tr(
            td("Downstream"),
            td(bytes(status.downstreamTraffic.bytesIn)), td(bytes(status.downstreamTraffic.bytesOut)),
            td(count(status.downstreamTraffic.packetsIn)), td(count(status.downstreamTraffic.packetsOut)),
            td(status.downstreamTraffic.currentConnections), td(status.downstreamTraffic.totalConnections)
          )
        )
      )
    )
  }

  def requests(status: Status): Frag = {
    // Split the response-statuses into timer-groups
    val responses = status.responses groupBy (_.responseType) mapValues (_.head.stats)

    // Prepare histogram table portions
    def timer(title: String, count: Long, data: Option[Timer]): Frag = {
      // Render-helper
      def valid(f: Timer => String): String = data map (f(_)) getOrElse "N/A"

      tr(
        td(title),
        td(if (count >= 0) count.toString else ""),
        td(valid(_.count.toString)),
        td(valid(_.min.nanos.toMillis + "ms")),
        td(valid(_.mean.nanos.toMillis + "ms")),
        td(valid(_.`95th`.nanos.toMillis + "ms")),
        td(valid(_.max.nanos.toMillis + "ms")),
        td(valid(t => "%.2f/s".format(t.`1min`))),
        td(valid(t => "%.2f/s".format(t.`5min`))),
        td(valid(t => "%.2f/s".format(t.`15min`)))
      )
    }


    div(cls := "table-responsive",
      table(cls := "table table-striped",
        width := "100%", "cellspacing".attr := "0", "cellpadding".attr := "0", style := "width:100%;border:1px solid;padding:4px;",
        thead(
          td("Type"),
          td("Active", style := "width:8%"),
          td("Total", style := "width:8%"),
          td("Min Elapsed", style := "width:9%"),
          td("Avs Elapsed", style := "width:9%"),
          td("95% Elapsed", style := "width:9%"),
          td("Max Elapsed", style := "width:9%"),
          td("Last Min Rate", style := "width:9%"),
          td("Last 5m Rate", style := "width:9%"),
          td("Last 15m Rate", style := "width:9%")
        ),
        tbody(
          timer(
            "Request Sessions", status.sessions.active, Option(status.sessions.stats) //Metrics.SessionActive.count, Metrics.SessionDuration
          ),
          timer(
            "HTTP Requests", status.requests.active, Option(status.requests.stats) //Metrics.RequestsOpen.count, Metrics.RequestsCompleted
          ),
          timer(
            "HTTP Requests - 2xx Response", -1, responses get "2xx"
          ),
          timer(
            "HTTP Requests - 4xx Response", -1, responses get "4xx"
          ),
          timer(
            "HTTP Requests - 5xx Response", -1, responses get "5xx"
          ),
          timer(
            "HTTP Requests - Incomplete", -1, responses get "incomplete"
          ),
          timer(
            "HTTP Requests - Other", -1, responses get "other")
        )
      )
    )
  }

  def hostinfo(info: HostInfo): Frag =
    div(cls :="table-responsive",
      table(cls :="table table-striped",
        width:="100%", "cellspacing".attr:="0", "cellpadding".attr:="0", style:="width:100%;border:1px solid;padding:4px;",

        thead(
          td("Hostname"),
          td("Address"),
          td("Last Restart Time")
        ),
        tbody(
          tr(
            td(info.hostname),
            td(info.address),
            td(info.lastRestart)
          )
        )
      )
    )

  def hostinfo(): Frag =
    div(cls :="table-responsive",
      table(cls :="table table-striped",
        width:="100%", "cellspacing".attr:="0", "cellpadding".attr:="0", style:="width:100%;border:1px solid;padding:4px;",

        thead(
          td("Property"),
          td("Detail", width:="85%")
        ),
        tbody(
          tr(
            td("Last Restart Time"),
            td(starttime.toString)
          ),
          tr(
            td("Hostname"),
            td(str(ServerContext.fullHostName))
          ),
          tr(
            td("Canonical Hostname"),
            td(str(ServerContext.canonicalHostName))
          ),
          tr(
            td("IP/Address"),
            td(str(ServerContext.hostAddress))
          )

          /*tr(
            td("Local Addresses"),
            td(str(NetworkUtils.cachedNames mkString ", "))
          ),
          */
        )
      )
    )
}