package com.ebay.neutrino.channel

import java.util

import io.netty.buffer.{ByteBuf, ByteBufUtil}
import io.netty.channel.ChannelId

/**
 * Cut and paste "re" implementation of the DefaultChannelId.
 *
 * This class provides globally-unique IDs, but unfortunately is both final and
 * package-private, and hence can't be serialized or used for cross-instance IDs
 * as is required by UUID.
 *
 * @see io.netty.channel.DefaultChannelId
 */
case class NeutrinoChannelId(data: Array[Byte]) extends ChannelId {
  import com.ebay.neutrino.channel.DefaultChannelUtil._

  assert(data.length == DATA_LEN)

  def machineId()  = readLong(data, 0)
  def processId()  = readInt (data, MACHINE_ID_LEN)
  def sequenceId() = readInt (data, MACHINE_ID_LEN+PROCESS_ID_LEN)// SEQUENCE_LEN
  def timestamp()  = readLong(data, MACHINE_ID_LEN+PROCESS_ID_LEN+SEQUENCE_LEN) // TIMESTAMP_LEN
  def random()     = readInt (data, MACHINE_ID_LEN+PROCESS_ID_LEN+SEQUENCE_LEN+TIMESTAMP_LEN)


  override val asShortText: String =
    ByteBufUtil.hexDump(data, MACHINE_ID_LEN + PROCESS_ID_LEN + SEQUENCE_LEN + TIMESTAMP_LEN, RANDOM_LEN);

  override val asLongText: String = {
    val buf = new StringBuilder(2 * data.length + 5)
    var i = 0
    buf.append(ByteBufUtil.hexDump(data, i, MACHINE_ID_LEN)); i += MACHINE_ID_LEN
    buf.append('-')
    buf.append(ByteBufUtil.hexDump(data, i, PROCESS_ID_LEN)); i += PROCESS_ID_LEN
    buf.append('-')
    buf.append(ByteBufUtil.hexDump(data, i, SEQUENCE_LEN)); i += SEQUENCE_LEN
    buf.append('-')
    buf.append(ByteBufUtil.hexDump(data, i, TIMESTAMP_LEN)); i += TIMESTAMP_LEN
    buf.append('-')
    buf.append(ByteBufUtil.hexDump(data, i, RANDOM_LEN)); i += RANDOM_LEN
    assert(i == data.length)
    buf.toString
  }

  override lazy val hashCode: Int = random()

  override def compareTo(o: ChannelId): Int = 0

  override val toString = s"Id[$asLongText]"

  override def equals(rhs: Any): Boolean =
    rhs match {
      case id: NeutrinoChannelId => (id canEqual this) && util.Arrays.equals(data, id.data)
      case _ => false
    }
}


object NeutrinoChannelId {

  /**
   * Initialize our ChannelId from its component parts.
   *
   * @see io.netty.channel.DefaultChannelId#init()
   */
  def apply(): NeutrinoChannelId = {
    val bytes = DefaultChannelUtil.newInstanceBytes()
    NeutrinoChannelId(bytes)
  }

  def apply(buffer: ByteBuf): NeutrinoChannelId = {
    require(DefaultChannelUtil.DATA_LEN == buffer.readableBytes)

    val bytes = new Array[Byte](DefaultChannelUtil.DATA_LEN)
    buffer.getBytes(0, bytes)

    NeutrinoChannelId(bytes)
  }
}