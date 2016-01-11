package com.ebay.neutrino.www

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Props, ActorSystem, ScalaActorRef}
import akka.pattern.ask
import akka.util.Timeout
import com.ebay.neutrino.{SLB, NeutrinoPoolId}
import com.ebay.neutrino.api.ApiData
import com.ebay.neutrino.cluster.{SLBTopology, SystemConfiguration}
import com.ebay.neutrino.www.ui.SideMenu
import com.ebay.neutrino.www.ui.PageFormatting
import com.ebay.neutrino.cluster.SLBLoader


import com.typesafe.config.ConfigRenderOptions
import com.typesafe.scalalogging.slf4j.StrictLogging
import spray.http.StatusCodes

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}


trait WebService extends spray.routing.HttpService with ApiData with PageFormatting with StrictLogging
{
  def system: ActorSystem

  def topology = SystemConfiguration(system).topology

  val poolPage   = new SideMenu("Pools") with PoolsPage
  val serverPage = new SideMenu("Servers") with ServersPage

  def webRoutes =
    path ("activity") {
      complete {
        import PageFormatting.ScalaTagsPrettyMarshaller
        ActivityPage.page()
      }
    } ~
    path("pools") {
      complete {
        import PageFormatting.ScalaTagsPrettyMarshaller
        poolPage.summary(topology.toSeq)
      }
    } ~
    path("pools" / Segment) { id =>
      complete {
        import PageFormatting.ScalaTagsPrettyMarshaller
        val pool = topology.getPool(NeutrinoPoolId(id))
        poolPage.detail(pool)
      }
    } ~
    path("servers") {
      complete {
        import PageFormatting.ScalaTagsPrettyMarshaller
        val pools    = topology.toSeq
        val services = topology.asInstanceOf[SLBTopology].core.services
        val nodes    = services flatMap (_.pools()) flatMap (_.nodes())
        serverPage.summary(pools, nodes.toSeq)
      }
    } ~
    path("refresh") {
      complete {
        import PageFormatting.ScalaTagsPrettyMarshaller
        implicit val timeout = Timeout(FiniteDuration(3, TimeUnit.SECONDS))
        // Wait for the result, since refresh api has to be synchronous
        val reloader = Await.result(system.actorSelection("user/loader").resolveOne(), timeout.duration)
        val future = reloader ? "reload"
        val result = Await.result(future, timeout.duration)
        if (result == "complete") {
            logger.warn("Config reloaded, Successfully completed")
        } else {
          logger.warn("Unable to load the configuration")
        }
        poolPage.summary(topology.toSeq)
      }
    } ~
    path("config") {
      complete {
        val sysconfig = SystemConfiguration(system)
        sysconfig.config.root.render(ConfigRenderOptions.defaults)
      }
    } ~
    pathEndOrSingleSlash {
      complete {
        import PageFormatting.ScalaTagsPrettyMarshaller
        Overview.generate(generateStatus())
      }
    } ~
    get {
      redirect("/", StatusCodes.PermanentRedirect)
    }

}




