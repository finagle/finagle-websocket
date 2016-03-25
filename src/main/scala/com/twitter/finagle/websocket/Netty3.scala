package com.twitter.finagle.websocket

import com.twitter.finagle.Stack
import com.twitter.finagle.netty3.Netty3Listener
import com.twitter.finagle.server.Listener
import org.jboss.netty.channel.{Channels, ChannelPipelineFactory}
import org.jboss.netty.handler.codec.http.{HttpResponseEncoder, HttpRequestDecoder}

private[finagle] object Netty3 {
  private def serverPipeline = {
    val pipeline = Channels.pipeline()
    pipeline.addLast("decoder", new HttpRequestDecoder)
    pipeline.addLast("encoder", new HttpResponseEncoder)
    pipeline.addLast("handler", new WebSocketServerHandler)
    pipeline
  }

  def newListener[In, Out](params: Stack.Params): Listener[In, Out] =
    Netty3Listener(new ChannelPipelineFactory {
      def getPipeline() = serverPipeline
    }, params)
}
