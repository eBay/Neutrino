package com.ebay.neutrino.handler.ops

import com.ebay.neutrino.NeutrinoRequest
import io.netty.buffer.{Unpooled, ByteBufUtil, ByteBuf, ByteBufHolder}
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpContent
import org.scalatest.{FlatSpec, Matchers}


/**
 * Let's be perfectly clear - this unit-test exists only to bolster the code-coverage
 * of the underlying class.
 */
class NeutrinoAuditHandlerTest extends FlatSpec with Matchers
{
  behavior of "Neutrino-audit handler"


/*
  def request() = new NeutrinoRequest()


  it should "log read events" in {

    val channel = new EmbeddedChannel(new NeutrinoAuditHandler())
    val request = new NeutrinoRequest()
    channel.request =

    //


    override def userEventTriggered(ctx: ChannelHandlerContext, event: AnyRef): Unit = {
      state(ctx) map { state =>
        event match {
          case event: NeutrinoEvent => state.add(Event(event))
          case _ => state.add(UserEvent(event))
        }
      }
      ctx.fireUserEventTriggered(event)
    }
  }
*/


  it should "calculate size correctly for various types" in {
    val handler = new NeutrinoAuditHandler()

    handler.calculateSize(null) should be (0)
    handler.calculateSize("") should be (0)

    // Byte-buffer
    val buffer = Unpooled.wrappedBuffer("some string".getBytes)
    handler.calculateSize(buffer) should be (11)

    // Byte-holder
    val holder = new DefaultHttpContent(Unpooled.wrappedBuffer("other".getBytes))
    handler.calculateSize(holder) should be (5)
  }
}


