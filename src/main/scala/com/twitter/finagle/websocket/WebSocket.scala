package com.twitter.finagle.websocket

import com.twitter.concurrent.Offer
import com.twitter.util.{Future, Promise}
import java.net.URI
import org.jboss.netty.handler.codec.http.websocketx.WebSocketVersion

case class WebSocket(
  messages: Offer[String],
  uri: URI,
  headers: Map[String, String] = Map.empty[String, String],
  version: WebSocketVersion = WebSocketVersion.V13,
  release: Promise[Unit] = new Promise[Unit])
{
  def onClose = release.onSuccess(_)
}
