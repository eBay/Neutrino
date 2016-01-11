package com.ebay.neutrino.www.ui

import akka.actor.ActorSystem
import spray.routing.Route


trait ResourceServices extends spray.routing.HttpService
{
  def system: ActorSystem
  def getPathFromResourceDirectory(path: String): Route =
    pathPrefix (path) {
      get { getFromResourceDirectory(path) }
    }


  def resourceRoutes =
    pathPrefix ("assets") {
      get {
        getFromResourceDirectory("assets")
      }
    } ~
    pathPrefix ("css") {
      get {
        getFromResourceDirectory("css")
      }
    } ~
    pathPrefix ("js") {
      get {
        getFromResourceDirectory("js")
      }
    }

}




