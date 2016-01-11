package com.ebay.neutrino.www.ui



import scalatags.Text.TypedTag


trait Page extends PageFormatting {

  import scalatags.Text.all._
  //import scala.language.implicitConversions

  val CSS = "/css/dashboard.css"

  // Additional html components
  val nav = "nav".tag[String]
  val defer = "defer".attr

  // Defaults; should be overridden by children/implementors
  def pagename: String = "Neutrino"
  def prettyPrint: Boolean = true



  //css/jumbotron.css
  def header(customStylesheet: Option[String]) = {
    head(
      title := "Neutrino SLB Dashboard",

      meta(charset := "utf-8"),
      meta(httpEquiv := "X-UA-Compatible", content := "IE=edge"),
      meta(name :="viewport", content:="width=device-width, initial-scale=1"),
      //meta(description := "Neutrino SLB"),
      //meta(author := "cbrawn"),

      link(href := "/favicon.ico", rel :="icon"),

      // <!-- Bootstrap core CSS -->
      link(href := "/css/bootstrap.min.css", rel :="stylesheet"),
      // MIT license http://datatables.net/license/
      link(href := "http://cdn.datatables.net/1.10.5/css/jquery.dataTables.css", rel :="stylesheet"),

    // <!-- Custom styles for this template -->
      if (customStylesheet.isDefined)
        link(href := customStylesheet.get, rel := "stylesheet")
      else
        div()


    )
  }


  def onLoad(frags: Frag*): Frag = {
    if (frags.isEmpty)
      ()
    else {
      script(
        """$(document).ready(function(){""",
        frags,
        """});""")
    }
  }


  // Layout the full page
  def page(bodyFragment: Frag*): Frag =
    page(bodyFragment, Seq())

  def pageWithLoad(bodyFragment: Frag*)(onLoads: Frag*): Frag =
    page(bodyFragment, onLoads)


  def page(bodyFragment: Seq[Frag], onLoads: Seq[Frag], pretty: Boolean=true): Frag = {
    html(lang := "en",
      header(Option(CSS)),
      body(
        navigation(),

        div(cls :="container-fluid",
          div(cls := "row",
            menu(),

            div(cls := "col-sm-9 col-sm-offset-3 col-md-10 col-md-offset-2 main",
              bodyFragment
            )
          )
        ),


        // <!-- Bootstrap core JavaScript ================================================== -->
        // <!-- Placed at the end of the document so the pages load faster -->
        // ?? should we put jquery inline?
        script(src := "https://ajax.googleapis.com/ajax/libs/jquery/1.11.2/jquery.min.js", " "),
        // MIT license http://datatables.net/license/
        script(src := "http://cdn.datatables.net/1.10.5/js/jquery.dataTables.min.js", " "),
        script(src := "/js/bootstrap.min.js", " "),

        script(src := "/assets/js/docs.min.js", " "),

          // <!-- IE10 viewport hack for Surface/desktop Windows 8 bug -->
        script(src := "/assets/js/ie10-viewport-bug-workaround.js", " "),

        // Kick off any page-wide on-load events
        onLoad(onLoads)
      )
    )
  }


  def navigation() =
    nav(cls := "navbar navbar-inverse navbar-fixed-top",
      div(cls := "container-fluid",
        div(cls := "navbar-header",
          button(cls := "navbar-toggle collapsed",
            `type` := "button",
            data.toggle := "collapse",
            data.target := "#navbar",
            aria.expanded := "false",
            aria.controls := "navbar",

            span(cls := "sr-only", "Toggle navigation"),
            span(cls := "icon-bar"),
            span(cls := "icon-bar"),
            span(cls := "icon-bar")
          ),
          a(cls :="navbar-brand", href := "#", "Neutrino Load Balancer")
        )
      )
    )


  def pagelink(link: String, name: String) =
    if (name == pagename)
      li(cls :="active", a(href :=link, name, span(cls :="sr-only", " (current)")))
    else
      li(a(href :=link, name))


  def menu(): TypedTag[String]
}