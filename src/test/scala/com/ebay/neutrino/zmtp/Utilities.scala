package com.ebay.neutrino.zmtp

import java.nio.ByteOrder

import io.netty.buffer.ByteBuf


object Utilities {

  import io.netty.buffer.ByteBufUtil._


  implicit class ByteBufSupport(val self: ByteBuf) extends AnyVal {

    def isBigEndian() = (self.order eq ByteOrder.BIG_ENDIAN)


    def readOptionalByte(): Option[Byte] =
      if (self.isReadable) Option(self.readByte) else None


    def readLongByEndian() = isBigEndian match {
      case true  => self.readLong
      case false => swapLong(self.readLong)
    }

    def writeLongByEndian(value: Long) = isBigEndian match {
      case true  => self.writeLong(value)
      case false => self.writeLong(swapLong(value))
    }

    def copyToByteArray(): Array[Byte] = {
      val bytes = new Array[Byte](self.readableBytes())
      self.getBytes(self.readerIndex, bytes)
      bytes
    }
  }
}

// Hack around Option/Iterator support
object OptionIterator {

  def continually[T](elem: => Option[T]): Iterator[T] =
    Iterator.continually(elem) takeWhile (_.isDefined) flatten
}