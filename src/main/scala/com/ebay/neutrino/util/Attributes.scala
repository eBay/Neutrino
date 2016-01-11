package com.ebay.neutrino.util

import com.ebay.neutrino._
import com.ebay.neutrino.balancer.Balancer
import com.ebay.neutrino.channel.{NeutrinoPipelineChannel, NeutrinoService, NeutrinoSession}
import com.ebay.neutrino.handler.ops.{ChannelStatistics, ChannelTimeout}
import io.netty.channel._
import io.netty.util.{Attribute, AttributeKey, AttributeMap}


/**
 * SIP 15 (Static helper) Support for managing known attribute-keys on the
 * channel (or indirectly via the context).
 *
 * This would be easier to do with view-classes, but the compiler is considering them as field annotation:
 *  implicit class AttributeKeySupport[C <% Channel](val self: C)
 *
 * @param self
 */
object AttributeSupport {

  val BalancerKey     = AttributeKey.valueOf[NeutrinoCore]("balancer")
  val RequestKey      = AttributeKey.valueOf[NeutrinoRequest]("request")
  val SessionKey      = AttributeKey.valueOf[NeutrinoSession]("session")
  val ServiceKey      = AttributeKey.valueOf[NeutrinoService]("service")
  val TimeoutKey      = AttributeKey.valueOf[ChannelTimeout]("timeouts")
  val StatisticsKey   = AttributeKey.valueOf[ChannelStatistics]("statistics")


  /**
   * Generalized attribute-map helper functions, for AttributeMap subtypes (Channel, Context)
   *
   * These must be chained on the underlying AttributeMap class (rather than inherited
   * directly in the caller value-class, as that forces an object instantiation.
   *
   * @param self
   */
  implicit class AttributeMapSupport(val self: AttributeMap) extends AnyVal {

    // Resolve attribute from the underlying container
    def attr[T](key: AttributeKey[T]): Attribute[T] = self.attr(key)

    // Generic retrieval method
    def get[T](key: AttributeKey[T]): Option[T] =
      Option(attr(key).get())

    // Generic retrieval method, with default
    def getOrElse[T](key: AttributeKey[T])(default: => T): T =
      get(key) getOrElse (set(key, Option(default)).get)

    // Generic setter/clear method
    def set[T](key: AttributeKey[T], value: Option[T]): Option[T] = value match {
      case Some(set) => attr(key).set(set); value
      case None => attr(key).remove(); value
    }
  }


  /**
   * Attribute-based support methods.
   *
   */
  implicit class AttributeValueSupport(val self: AttributeMap) extends AnyVal {

    // We provide a structural definition for this so our static 'statistics' method
    // doesn't need to create an anonymous method
    private def createStatistics = new ChannelStatistics()


    // The balancer-connection (request/response context)
    def request = self.get(RequestKey)
    def request_=(value: NeutrinoRequest) = self.set(RequestKey, Option(value))
    def request_=(value: Option[NeutrinoRequest]) = self.set(RequestKey, value)

    // Neutrino-Session (user-pipeline) getter and setter
    def session = self.get(SessionKey)
    def session_=(value: NeutrinoSession) = self.set(SessionKey, Option(value))
    def session_=(value: Option[NeutrinoSession]) = self.set(SessionKey, value)

    // Neutrino-Service (wrapper around core) getter and setter
    def service = self.get(ServiceKey)
    def service_=(value: NeutrinoService) = self.set(ServiceKey, Option(value))
    def service_=(value: Option[NeutrinoService]) = self.set(ServiceKey, value)

    // Channel timeout support
    def timeout = self.get(TimeoutKey)
    def timeout_=(value: ChannelTimeout) = self.set(TimeoutKey, Option(value))
    def timeout_=(value: Option[ChannelTimeout]) = self.set(TimeoutKey, value)

    // IO-Channel Statistics lazy-getter
    def statistics = self.get(StatisticsKey) match {
      case Some(stats) => stats
      case None => self.set(StatisticsKey, Option(new ChannelStatistics())).get
    }

  }


  /**
   * Attribute support for NeutrinoRequest (class-key mapped) attributes.
   * @param self
   */
  implicit class RequestAttributeSupport(val self: NeutrinoRequest) extends AnyVal {

    def balancer = self.get(classOf[Balancer])
    def balancer_=(value: Balancer) = self.set(classOf[Balancer], value)
    def balancer_=(value: Option[Balancer]) = self.set(classOf[Balancer], value getOrElse null)

    def node = self.get(classOf[NeutrinoNode])
    def node_=(value: NeutrinoNode) = self.set(classOf[NeutrinoNode], value)
    def node_=(value: Option[NeutrinoNode]) = self.set(classOf[NeutrinoNode], value getOrElse null)

    // Neutrino Pipeline only: downstream context
    def downstream = self.get(classOf[Channel])
    def downstream_=(value: Channel) = self.set(classOf[Channel], value)
    def downstream_=(value: Option[Channel]) = self.set(classOf[Channel], value getOrElse null)
  }

}



/**
 * Support for dynamic class-based attributes, which can be mixed into other classes for
 * 'context' support, allowing dynamic class-specific storage and resolution.
 *
 * Note that we don't really support null (as a null value gets mapped to None)
 */
class AttributeClassMap {

  val attributes = new java.util.HashMap[Class[_], AnyRef]()

  // Optionally get the value belonging to <clazz>
  def get[T <: AnyRef](clazz: Class[T]): Option[T] =
    Option(attributes.get(clazz).asInstanceOf[T])

  // Get the value belonging to <clazz>, defaulting to the provided
  def get[T <: AnyRef](clazz: Class[T], default: => T): T =
    attributes.get(clazz) match {
      case null  => val value = default; attributes.put(clazz, value); value
      case value => value.asInstanceOf[T]
    }

  // Set/Clear the value belonging to <clazz>
  def set[T <: AnyRef](clazz: Class[T], value: T): Unit =
    value match {
      case null  => attributes.remove(clazz)
      case value => attributes.put(clazz, value)
    }

  // Explicitly clear the value belonging to <clazz>
  def clear[T <: AnyRef](clazz: Class[T]): Option[T] =
    Option(attributes.remove(clazz)).asInstanceOf[Option[T]]
}