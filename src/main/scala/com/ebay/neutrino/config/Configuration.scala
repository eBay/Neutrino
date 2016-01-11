package com.ebay.neutrino.config

import java.io.File
import java.lang.reflect.Constructor
import java.net.{URI, URL}
import java.util.concurrent.TimeUnit

import com.typesafe.config.{Config, ConfigFactory, ConfigList, ConfigValue}
import com.typesafe.scalalogging.slf4j.StrictLogging

import scala.util.{Failure, Success, Try}


/**
 * Configuration static helper methods.
 */
trait HasConfiguration { def config: Config }


object Configuration extends StrictLogging {
  import scala.collection.JavaConversions._
  import scala.concurrent.duration._


  /**
   * Load the configuration from the filename and environment provided.
   *
   * @param filename
   * @param environment
   */
  def load(filename: String = null, environment: String = null, loader: ClassLoader = null): Config = {

    val basecfg = (filename, loader) match {
      case (null, null) =>
        logger.info("Loading default configuration.")
        ConfigFactory.load()
      case (null, _) =>
        logger.info("Loading default configuration with provided ClassLoader.")
        ConfigFactory.load(loader)
      case (_, null) =>
        logger.info("Loading configuration from file {}", filename)
        val slbFile = new File(filename)
        val slbConfig = ConfigFactory.parseFile(slbFile)
        if (slbConfig.isEmpty) {
          ConfigFactory.load(filename)
        } else {
          ConfigFactory.load(slbConfig)
        }
      case _ =>
        logger.info("Loading configuration from file {} using provided ClassLoader", filename)
        ConfigFactory.load(loader, filename)
    }

    load(basecfg, environment)
  }

  /**
   * Load the configuration from the concrete config-object and environment provided.
   *
   * @param basecfg
   * @param environment
   */
  def load(basecfg: Config, environment: String): Config = {

    // If environment is provided, attempt to extract the environment-specific subtree
    val envcfg = Option(environment) match {
      case Some(envpath) if basecfg.hasPath(envpath) =>
        logger.warn("Merging with environmental configuration {}", envpath)
        basecfg.getConfig(envpath) withFallback basecfg
      case Some(envpath) =>
        logger.error("Unable to merge with environmental configuration {}; not found", envpath)
        basecfg
      case None =>
        basecfg
    }

    // Extract our concrete configuration from the ebay-neutrino tree
    val neutrino = envcfg.getConfig("neutrino")

    // Finally, re-stitch together the timeout defaults
    val timeout = neutrino.getConfig("timeout")

    neutrino
      .withValue("pool.timeout", neutrino.getValue("pool.timeout").withFallback(timeout))
  }


  implicit class ConfigSupport(val self: Config) extends AnyVal {

    // Resolve the constructor provided, skipping unavailable ones
    def constructor[T](ctor: => Constructor[T]): Option[Constructor[T]] =
      Try(ctor) match {
        case Success(ctor) => Option(ctor)
        case Failure(x: NoSuchMethodException) => None
        case Failure(x) => throw x
      }

    def optional[T](path: String, f: String => T): Option[T] = self hasPath path match {
      case true => Option(f(path))
      case false => None
    }


    def getIntOrList(path: String): Seq[Int] = self getValue path match {
      case list: ConfigList => (self getIntList path map (_.toInt)).toList
      case _ => Seq(self getInt path)
    }

    def getStringOrList(path: String): Seq[String] = self getOptionalValue path match {
      case None => Seq()
      case Some(list: ConfigList) => (self getStringList path).toList
      case Some(_) => Seq(self getString path)
    }

    def getMergedConfigList(path: String, defaultPath: String): List[_ <: Config] = {
      val default = self getConfig defaultPath
      self getConfigList(path) map (_ withFallback default) toList
    }

    def getDuration(path: String): FiniteDuration =
      self getDuration(path, TimeUnit.NANOSECONDS) nanos

    def getProtocol(path: String): Transport =
      Transport(self getString path)

    def getTimeouts(path: String): TimeoutSettings =
      TimeoutSettings(self getConfig path)

    def getUrl(path: String): URL =
      new URL(self getString path)

    def getUri(path: String): URI =
      URI.create(self getString path)

    def getClass[T](path: String): Class[T] =
      Class.forName(self getString path).asInstanceOf[Class[T]]

    def getClassList[T](path: String): List[Class[T]] =
      self getStringList path map (Class.forName(_).asInstanceOf[Class[T]]) toList

    def getClassInstances[T](path: String): List[T] =
      self getStringOrList path map (Class.forName(_).newInstance().asInstanceOf[T]) toList

    def getInstance[T](path: String): Option[T] =
      constructor(Class.forName(self getString path).getConstructor()) map (_.newInstance().asInstanceOf[T])

    def getConfigInstance[T](path: String): Option[T] =   // Configuration-aware instance
      constructor(Class.forName(self getString path).getConstructor(classOf[Config])) map (_.newInstance(self).asInstanceOf[T])



    def getOptionalValue(path: String): Option[ConfigValue] =
      optional(path, self getValue)

    def getOptionalConfig(path: String) =
      optional(path, self getConfig)

    def getOptionalInt(path: String) =
      optional(path, self getInt)

    def getOptionalString(path: String) =
      optional(path, self getString)

    def getOptionalConfigList(path: String): List[_ <: Config] =
      optional(path, self getConfigList) map (_.toList) getOrElse List()

    def getOptionalDuration(path: String): Option[FiniteDuration] =
      optional(path, getDuration) filterNot (_ == Duration.Zero)

    def getOptionalClass[T](path: String): Option[Class[T]] =
      optional(path, self getString) map (Class.forName(_).asInstanceOf[Class[T]])

    def getOptionalClassList[T](path: String): List[Class[T]] =
      optional(path, getClassList[T]) getOrElse List[Class[T]]()

    def getOptionalInstance[T](path: String): Option[T] =
      optional(path, self getInstance).flatten

    def getOptionalConfigInstance[T](path: String): Option[T] =
      optional(path, self getConfigInstance).flatten
  }
}