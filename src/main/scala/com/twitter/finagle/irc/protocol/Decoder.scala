package com.twitter.finagle.irc.protocol

object Decoder {
  private val NeedMoreData = null
  private val Delimiter = ChannelBuffers.wrappedBuffer("\r\n".getBytes)
  private val DelimiterLength = Delimiter.capacity
  private val FindCRLF = new ChannelBufferIndexFinder() {
    def find(buffer: ChannelBuffer, guessedIndex: Int): Boolean = {
      val enoughBytesForDelimeter = guessedIndex + Delimiter.readableBytes
      if (buffer.writerIndex < enoughBytesForDelimeter) return false

      val cr = buffer.getByte(guessedIndex)
      val lf = buffer.getByte(guessedIndex + 1)
      cr == '\r' && lf == '\n'
    }
  }
}

class IrcDecoder extends FrameDecoder {
  import Decoder._

  def decode(ctx: ChannelHandlerContext, channel: Channel, buf: ChannelBuffer): Message = {
    val frameLength = buffer.bytesBefore(FindCRLF)
    if (frameLength < 0) NeedMoreData else {
      val frame = buf.slice(buffer.readerIndex, frameLength)
      buf.skipBytes(frameLength + DelimiterLength)

      Array(code, args) = frame.toString(CharsetUtil.UTF_8).split(' ', 1)
      code match {
        case "PASS" => Pass(args)
        case "NICK" => Nick.decode(args)
      }
    }
  }
}
