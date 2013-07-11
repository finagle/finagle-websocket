package com.twitter.finagle.irc.protocol

import org.jboss.netty.handler.codec.frame.FrameDecoder
import org.jboss.netty.util.CharsetUtil
import org.jboss.netty.channel._
import org.jboss.netty.buffer.{
  ChannelBuffer, ChannelBuffers, ChannelBufferIndexFinder}

class Decoder extends FrameDecoder {
  private[this] val NeedMoreData = null
  private[this] val Delimiter = ChannelBuffers.wrappedBuffer("\r\n".getBytes)
  private[this] val DelimiterLength = Delimiter.capacity
  private[this] val DecimalRegex = """^\d+$""".r
  private[this] val FindCRLF = new ChannelBufferIndexFinder() {
    def find(buffer: ChannelBuffer, guessedIndex: Int): Boolean = {
      val enoughBytesForDelimeter = guessedIndex + Delimiter.readableBytes
      if (buffer.writerIndex < enoughBytesForDelimeter) return false

      val cr = buffer.getByte(guessedIndex)
      val lf = buffer.getByte(guessedIndex + 1)
      cr == '\r' && lf == '\n'
    }
  }

  def decode(ctx: ChannelHandlerContext, channel: Channel, buf: ChannelBuffer): Message = {
    val frameLength = buf.bytesBefore(FindCRLF)
    if (frameLength < 0) NeedMoreData else {
      val frame = buf.slice(buf.readerIndex, frameLength)
      buf.skipBytes(frameLength + DelimiterLength)

      val cmdStr = frame.toString(CharsetUtil.UTF_8)
      println("< " + cmdStr)
      val cmd :: tail = cmdStr.split(":", 1).toList: List[String]
      cmd match {
        //case DecimalRegex => Response.get(cmd.toInt)
        case _ =>
          val tkns: List[String] = cmd.split(" ").toList ++ tail
          Protocol.decode(tkns) getOrElse { UnknownCmd(tkns.first, tkns.tail.mkString(" ")) }
      }
    }
  }
}
