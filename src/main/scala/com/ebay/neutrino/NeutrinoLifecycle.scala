package com.ebay.neutrino

import java.lang.reflect.Constructor

import com.typesafe.config.Config

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}


/**
 * Marker interface for BalancerLifecycle.
 *
 * This will be created reflectively if provided in dot-delimited class format in the
 * application.conf/reference.conf configuration file.
 *
 * It supports one of two reflective constructors:
 *  .newInstance()
 *  .newInstance(config: Config)
 */
trait NeutrinoLifecycle {

  // Component lifecycle starting
  def start(balancer: NeutrinoCore)

  // Component lifecycle stopping
  def shutdown(balancer: NeutrinoCore)
}


/**
 * Helper utility class for resolving initializers from the active balancer core.
 *
 * TODO rename to something akin to component
 * TODO rename class for easier Java API
 */
object NeutrinoLifecycle {

  // Try a one-parameter ctor for settings
  def create[T](clazz: Class[_ <: T], c: Config): Option[T] =
    constructor(clazz.getConstructor(classOf[Config])) map (_.newInstance(c))

  // Try a default (no-param) setting
  def create[T](clazz: Class[_ <: T]): Option[T] =
    constructor(clazz.getConstructor()) map (_.newInstance())

  // Resolve the constructor provided, skipping unavailable ones
  def constructor[T](ctor: => Constructor[T]): Option[Constructor[T]] =
    Try(ctor) match {
      case Success(ctor) => Option(ctor)
      case Failure(x: NoSuchMethodException) => None
      case Failure(x) => throw x
    }

  def hasConstructor[T](clazz: Class[_ <: T]): Boolean =
    constructor(clazz.getConstructor()).isDefined ||
      constructor(clazz.getConstructor(classOf[Config])).isDefined


  // Java-compatible version
  def getInitializer[T <: NeutrinoLifecycle](core: NeutrinoCore, clazz: Class[T]): Option[T] =
    core.component[T](clazz)

  // Java-compatible version
  def getInitializer[T <: NeutrinoLifecycle](request: NeutrinoRequest, clazz: Class[T]): Option[T] =
    getInitializer(request.session.service.core, clazz)

  // Convenience method; resolve an initializer/component by class
  def getInitializer[T <: NeutrinoLifecycle](connection: NeutrinoRequest)(implicit ct: ClassTag[T]): Option[T] =
    connection.session.service.core.component[T](ct.runtimeClass.asInstanceOf[Class[T]])
}