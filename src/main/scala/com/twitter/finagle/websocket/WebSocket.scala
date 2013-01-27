package com.twitter.finagle.websocket

import com.twitter.concurrent.Offer
import java.net.URI
import org.jboss.netty.handler.codec.http.websocketx.WebSocketVersion

trait WebSocket {
  def uri: URI
  def messages: Offer[String]
  def release()
  def headers = Map.empty[String, String]
  val version = WebSocketVersion.V13
}
