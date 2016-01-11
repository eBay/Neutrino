package com.ebay.neutrino.metrics

import nl.grons.metrics.scala._
import scala.collection.mutable


/**
 * This mixin trait can used for creating a class which is instrumented with metrics.
 *
 * Reimplented our own base-class instead of extending InstrumentedBuilder because
 * we wanted to support a subclass implementation of the MetricsBuilder class
 *  > extends nl.grons.metrics.scala.InstrumentedBuilder
 *
 */
trait Instrumented extends BaseBuilder {

  /**
   * The MetricBuilder that can be used for creating timers, counters, etc.
   */
  lazy val metrics =
    new MetricBuilder(metricBaseName, Metrics.metricRegistry) with InstrumentedBuilderSupport
}


trait InstrumentedBuilderSupport { self: MetricBuilder =>

  /** Helper; build a metric-name out of the components provided */
  def metricName(names: String*) = baseName.append(names:_*)

  /**
   * Registers a new gauge metric.
   */
  def gauge[A](metricName: MetricName, scope: String*)(f: => A): Gauge[A] =
    new Gauge[A](registry.register(metricName.append(scope:_*).name, new com.codahale.metrics.Gauge[A] {
      def getValue: A = f })
    )

  def gauge[A](clazz: Class[_], name: String*)(f: => A): Gauge[A] =
    gauge(MetricName(clazz, name:_*).name)(f)


  /**
   * Registers a new timer metric.
   */
  def timer(metricName: MetricName, scope: String*): Timer =
    new Timer(registry.timer(metricName.append(scope:_*).name))

  def timer(clazz: Class[_], name: String*): Timer =
    timer(MetricName(clazz, name:_*).name)


  /**
   * Registers a new meter metric.
   */
  def meter(metricName: MetricName, scope: String*): Meter =
    new Meter(registry.meter(metricName.append(scope:_*).name))

  def meter(clazz: Class[_], name: String*): Meter =
    meter(MetricName(clazz, name:_*).name)


  /**
   * Registers a new histogram metric.
   */
  def histogram(metricName: MetricName, scope: String*): Histogram =
    new Histogram(registry.histogram(metricName.append(scope:_*).name))

  def histogram(clazz: Class[_], name: String*): Histogram =
    histogram(MetricName(clazz, name:_*).name)


  /**
   * Registers a new counter metric.
   */
  def counter(name: String, scope: String*): Counter =
    counter(metricName(name +: scope:_*))

  def counter(metricName: MetricName, scope: String*): Counter =
    new Counter(registry.counter(metricName.append(scope:_*).name))

  def counter(clazz: Class[_], name: String*): Counter =
    counter(MetricName(clazz, name:_*).name)

  def counterset(name: String, scope: String*): CounterSet =
    new CounterSet(metricName(name +: scope:_*))


  /**
   * Hack - this works around writing a duplicate gauge (ie: with unit testing)
   * TODO replace this with a non-static MetricsRegistry
   */
  def safegauge[A](metricName: MetricName, scope: String*)(f: => A): Gauge[A] = {
    val metricname = metricName.append(scope: _*).name
    registry.remove(metricname)
    new Gauge[A](registry.register(metricname, new com.codahale.metrics.Gauge[A] { def getValue: A = f }))
  }

  def safegauge[A](name: String, scope: String = null)(f: => A): Gauge[A] =
    safegauge(baseName.append(name), scope)(f)



  class CounterSet(metricName: MetricName) {
    val set = mutable.Map[String, Counter]()

    def apply(name: String): Counter = set.getOrElseUpdate(name, counter(name))
    def apply(clazz: Class[_]): Counter = apply(clazz.getSimpleName)
  }
}