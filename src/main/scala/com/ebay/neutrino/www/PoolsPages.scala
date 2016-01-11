package com.ebay.neutrino.www

import com.ebay.neutrino.config.{CanonicalAddress, VirtualPool, WildcardAddress}
import com.ebay.neutrino.metrics.Instrumented
import com.ebay.neutrino.www.ui.{Page, PageFormatting}
import com.ebay.neutrino.{NeutrinoNode, NeutrinoPoolId}


/**
 * Result-set support with DataTables.
 * @see http://www.datatables.net/examples/data_sources/dom.html
 */
trait PoolsPage extends  PageFormatting with Instrumented { self: Page =>

  import scalatags.Text.all._

  /** Extract canonical-addresses from pool */
  @inline def canonicals(pool: VirtualPool): Seq[CanonicalAddress] =
    pool.addresses collect {
      case canonical: CanonicalAddress => canonical
    }


  def summary(pools: Seq[VirtualPool]): Frag = {
    pageWithLoad(
      div(
         h5(cls := "pull-right", a(href := s"/refresh", u("Reload Config"))),
         h1(cls := "page-header", "Pools")

       ),


      //h2(cls :="sub-header", "Applications Loaded"),
      div(cls :="table-responsive",
        table(id := "results", cls :="table table-striped",
          thead(
            tr(
              th("Pool Name"),
              th("Protocol"),
              th("Servers"),
              th("CNAME"),
              th("Balancer"),
              th("Timeouts")
            )
          ),
          tbody(
            pools map { pool =>
              val id = NeutrinoPoolId(pool.id, pool.protocol)
              val cname = (canonicals(pool) map (_.host) headOption) getOrElse "N/A"

              tr(
                td(a(href := s"/pools/$id", pool.id.split(":").head)),
                td(pool.protocol.toString.toUpperCase),
                td(a(href := s"/pools/$id", pool.servers.size+" Servers")),
                td(a(href := s"/pools/$id", cname)),
                td(pool.balancer.clazz.getSimpleName),
                td("Default")
              )
            }
          )
        )
      )
    )("""
        $('#results').DataTable();
      """)
  }


  // Build the detail page (as applicable)
  // TODO deal with not-found
  def detail(pool: Option[VirtualPool]): Frag =
    page(
      h1(cls := "page-header", s"Pool Detail"),

      pool match {
        case None =>
          Seq(p("Not found"))

        case Some(pool) =>
          Seq(
            h2(cls :="sub-header", "Details"),
            div(cls :="table-responsive",
              table(cls :="table table-striped",
                thead(
                  tr(
                    th("Pool ID"),
                    th("Type"),
                    th("Hostname (CNAME/A)"),
                    th("Source Port"),
                    th("IP Resolved"),
                    th("Routing Policy")
                  )
                ),
                tbody(
                  pool.addresses collect {
                    case address: CanonicalAddress =>
                      tr(
                        td(pool.id),
                        td("CNAME"),
                        td(address.host),
                        td(address.port),
                        td(address.socketAddress.toString),
                        td()
                      )

                    case address: WildcardAddress =>
                      tr(
                        td(pool.id),
                        td("L7 Rule"),
                        td(address.host),
                        td(address.port),
                        td(),
                        td(address.socketAddress.toString),
                        td(address.path)
                      )
                  }
                )
              )
            ),
            div(cls :="table-responsive",
              table(cls :="table table-striped",
                thead(
                  tr(
                    th("Read Idle Timeout"),
                    th("Write Idle Timeout"),
                    th("Write Completion Timeout"),
                    th("Request Timeout"),
                    th("Session Timeout")
                  )
                ),
                tbody(

                    tr(
                      td(pool.timeouts.readIdle.toSeconds+"s"),
                      td(pool.timeouts.writeIdle.toSeconds+"s"),
                      td(pool.timeouts.writeCompletion.toSeconds+"s"),
                      td(pool.timeouts.requestCompletion.toSeconds+"s"),
                      td(pool.timeouts.sessionCompletion.toSeconds+"s")
                    )

                )
              )
            ),

            h2(cls :="sub-header", "Servers"),
            div(cls :="table-responsive",
              table(cls :="table table-striped",
                thead(
                  tr(
                    th("ServerId"),
                    th("Host"),
                    th("Port"),
                    th("Health State")
                  )
                ),
                tbody(
                  pool.servers map { server =>
                    tr(
                      td(server.id),
                      td(server.host),
                      td(server.port),
                      td(server.healthState.toString)
                    )
                  }
                )
              )
            )

            //h2(cls :="sub-header", "Load Balancer")
          )
      }
    )
}


/**
 * Result-set support with DataTables.
 * @see http://www.datatables.net/examples/data_sources/dom.html
 */
trait ServersPage extends PageFormatting with Instrumented { self: Page =>

  import scalatags.Text.all._


  def summary(pools: Seq[VirtualPool], nodes: Seq[NeutrinoNode]=Seq()): Frag = {
    // If available, grab 'active' server info
    val nodemap = nodes.groupBy(_.settings) mapValues (_.head)

    pageWithLoad(
      h1(cls := "page-header", "Servers"),

      //h2(cls :="sub-header", "Applications Loaded"),
      div(cls :="table-responsive",
        table(id := "results", cls :="table table-striped",
          thead(
            tr(
              th("Host"),
              th("Port"),
              th("Health State"),
              th("Pool Name"),
              th("Active Connections"),
              th("Pooled Connections"),
              th("Last Activity")
            )
          ),
          tbody(
            pools map { pool =>
              pool.servers map { server =>
                val node = nodemap get (server)
                val id = NeutrinoPoolId(pool.id, pool.protocol)
                val allocated = node map (_.allocated.size.toString) getOrElse "-"
                val available = node map (_.available.size.toString) getOrElse "-"

                tr(
                  td(server.host),
                  td(server.port),
                  td(server.healthState.toString),
                  td(a(href := s"/pools/$id", pool.id.split(":").head)),
                  td(allocated),
                  td(available),
                  td("-")
                )
              }
            }
          )
        )
      )
    )("""
        $('#results').DataTable();
      """)
  }
}