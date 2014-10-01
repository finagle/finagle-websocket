package com.twitter.finagle.websocket

import com.twitter.concurrent.Offer
import com.twitter.util.{Duration, Future, Promise}
import java.net.URI
import org.jboss.netty.handler.codec.http.websocketx.WebSocketVersion
import java.net.SocketAddress

case class WebSocket(
  messages: Offer[String],
  binaryMessages: Offer[Array[Byte]],
  uri: URI,
  headers: Map[String, String] = Map.empty[String, String],
  remoteAddress: SocketAddress = new SocketAddress {},
  version: WebSocketVersion = WebSocketVersion.V13,
  onClose: Future[Unit] = new Promise[Unit],
  close: () => Unit = { () => () },
  var idlePingTimeout: Duration = Duration.Top)
