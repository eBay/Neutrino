package com.ebay.neutrino.www

import com.ebay.neutrino.api.ApiData
import com.ebay.neutrino.metrics.Instrumented
import com.ebay.neutrino.www.ui.PageFormatting
import com.ebay.neutrino.www.ui.SideMenu


object Overview extends SideMenu("Overview") with OverviewPage with PageFormatting with ApiData with Instrumented {

  import ApiData._

  import scalatags.Text.all._


  //Caller: val status = generateStatus(core)
  def generate(status: Status): Frag = {
    page(
      h1(cls := "page-header", "Overview"),

      h2(cls :="sub-header", "Traffic"),
      traffic(status),

      h2(cls := "sub-header", "Requests"),
      requests(status),

      h2(cls :="sub-header", "Host Information"),
      hostinfo()
    )
  }
}
