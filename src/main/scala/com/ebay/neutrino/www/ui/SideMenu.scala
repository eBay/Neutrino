package com.ebay.neutrino.www.ui


import com.ebay.neutrino.www.ui


class SideMenu(override val pagename: String="Neutrino", override val prettyPrint: Boolean=true)
  extends ui.Page
  with PageFormatting
{
  import scalatags.Text.all._


  def menu() = {
    def pagelink(link: String, name: String) =
      if (name == pagename)
        li(cls :="active", a(href :=link, name, span(cls :="sr-only", " (current)")))
      else
        li(a(href :=link, name))


    div(cls := "col-sm-3 col-md-2 sidebar",
      ul(cls := "nav nav-sidebar",
        pagelink("/", "Overview")
      ),
      ul(cls := "nav nav-sidebar",
        pagelink("/pools", "Pools"),
        pagelink("/servers", "Servers")
      ),
      ul(cls := "nav nav-sidebar",
        pagelink("/config", "Raw Configuration")
      )
    )
  }
}