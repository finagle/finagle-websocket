package com.twitter.finagle.websocket

import com.twitter.concurrent.Offer

trait WebSocket {
  def messages: Offer[String]
  def release()
}
