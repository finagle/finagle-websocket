package com.twitter.finagle.websocket

import com.twitter.finagle.{ Service, SimpleFilter }
import com.twitter.finagle.util.DefaultTimer
import com.twitter.util.{ Duration, Future, Time, Timer }

case class KeepAlive(
  interval: Duration,
  timer: Timer = DefaultTimer.twitter
) extends SimpleFilter[WebSocketStart, WebSocket] {
  def apply(req: WebSocketStart, svc: Service[WebSocketStart, WebSocket]): Future[WebSocket] =
    svc(req) map { sock =>
      val task = timer.schedule(interval) { sock.write(WebSocketFrame.Ping()) }
      sock.closed foreach { _ => task.cancel() }
      sock
    }
}

object PingPong extends SimpleFilter[WebSocket, Unit] {
  def apply(sock: WebSocket, svc: Service[WebSocket, Unit]): Future[Unit] = {
    sock.stream foreach {
      case WebSocketFrame.Ping(data) => sock.write(WebSocketFrame.Pong(data))
      case _ =>
    }
    svc(sock)
  }
}

