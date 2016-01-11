package com.ebay.neutrino.www

import com.ebay.neutrino.metrics.Instrumented
import com.ebay.neutrino.www.ui.PageFormatting
import com.ebay.neutrino.www.ui.SideMenu

import scala.concurrent.duration._


object ActivityPage extends SideMenu("Activity") with PageFormatting with Instrumented {

  import scalatags.Text.all._


  def page(): Frag = {
    page(
      h1(cls := "page-header", "Activity"),
      activity()
    )
  }


  def activity(): Frag =
    Seq(
      h2(cls :="sub-header", "Recent Activity"),

      div(cls :="table-responsive",
        table(cls :="table table-striped",
          width:="100%", "cellspacing".attr:="0", "cellpadding".attr:="0", style:="width:100%;border:1px solid;padding:4px;",

          thead(
            td(b("URL")),
            td(b("Pool")),
            td(b("Request Size")),
            td(b("Response Size")),
            td(b("Duration")),
            td(b("Status"))
          ),
          tbody(
            tr(
              td("www.ebay.com"),
              td("mryjenkins"),
              td(count(3500)),
              td(count(12050)),
              td(1500.milliseconds.toString),
              td("200 OK")
            ),
            td("jenkins.ebay.com"),
            td("mryjenkins"),
            td(count(3500)),
            td(count(100050)),
            td(300.milliseconds.toString),
            td("404 NOT FOUND")
          )
        )
      )
    )
}