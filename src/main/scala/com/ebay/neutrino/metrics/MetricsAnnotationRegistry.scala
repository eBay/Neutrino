package com.ebay.neutrino.handler

import java.lang.annotation.Annotation
import scala.collection.mutable

import com.codahale.metrics.annotation.{Gauge, Metered, Timed}
import io.netty.channel._


class MetricsAnnotationRegistry {

  // Constants
  val Inbound  = classOf[ChannelInboundHandler]
  val Outbound = classOf[ChannelOutboundHandler]
  val Methods  = Inbound.getMethods ++ Outbound.getMethods


  // Extract an interface-supported method matching the method-name, from the class provided.
  case class AnnotatedPair[T](clazz: Class[T], methodName: String) {
    require(Inbound.isAssignableFrom(clazz) || Outbound.isAssignableFrom(clazz), s"Class $clazz is not a handler interface")

    // Check interface for method-signature and find class's matching method
    // Extract all available annotations from the class-method provided.
    lazy val annotations = Methods find(_.getName == methodName) map (m =>
      clazz.getMethod(methodName, m.getParameterTypes:_*)) match {
      case Some(method) => method.getAnnotations
      case None => throw new IllegalArgumentException(s"Method $methodName not found in $clazz")
    }
  }

  val map = mutable.Map[AnnotatedPair[_], Array[Annotation]]()


  def getAnnotations(clazz: Class[_], methodName: String) = {
    val pair = AnnotatedPair(clazz, methodName)

    map getOrElseUpdate (pair, pair.annotations flatMap {
      case timed: Timed   => Option(timed)
      case meter: Metered => Option(meter)
      case gauge: Gauge   => Option(gauge)
      case _              => None // exception
    })
  }

}