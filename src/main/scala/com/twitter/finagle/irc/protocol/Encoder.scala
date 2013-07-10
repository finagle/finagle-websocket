package com.twitter.finagle.irc.protocol

import com.twitter.finagle.irc.{Message, Response}
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder
import org.jboss.netty.channel.{Channel, ChannelHandlerContext}
import org.jboss.netty.util.CharsetUtil
import org.jboss.netty.buffer.ChannelBuffers

class Encoder extends OneToOneEncoder {
  def encode(context: ChannelHandlerContext, channel: Channel, message: AnyRef) = message match {
    case m: Message => ChannelBuffers.wrappedBuffer((m.encode + "\r\n").getBytes(CharsetUtil.UTF_8))
  }
}
