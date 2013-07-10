package com.twitter.finagle.irc

import com.twitter.finagle.irc.protocol._
import com.twitter.finagle.{Codec, CodecFactory}
import org.jboss.netty.channel.{ChannelPipelineFactory, Channels}

object IrcCodec extends CodecFactory[Message, Response] {
  def server = Function.const {
    new Codec[Message, Response] {
      def pipelineFactory = new ChannelPipelineFactory {
        def getPipeline = {
          val pipeline = Channels.pipeline()
          pipeline.addLast("decoder", new Decoder)
          pipeline.addLast("encoder", new Encoder)
          pipeline.addLast("handler", new ServerHandler)
          pipeline
        }
      }
    }
  }

  def client = Function.const {
    new Codec[Message, Response] {
      def pipelineFactory = new ChannelPipelineFactory {
        def getPipeline = {
          val pipeline = Channels.pipeline()
          pipeline.addLast("decoder", new Decoder)
          pipeline.addLast("encoder", new Encoder)
          pipeline.addLast("handler", new ClientHandler)
          pipeline
        }
      }
    }
  }
}
