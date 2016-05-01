package com.twitter.finagle.websocket

import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.dispatch.GenSerialClientDispatcher
import com.twitter.finagle.stats.{NullStatsReceiver, StatsReceiver}
import com.twitter.finagle.transport.Transport
import com.twitter.io.Buf
import com.twitter.util.{Closable, Future, Promise, Time}
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.handler.codec.http.websocketx.CloseWebSocketFrame

private[finagle] class ClientDispatcher(
    trans: Transport[Any, Any],
    statsReceiver: StatsReceiver)
  extends GenSerialClientDispatcher[Request, Response, Any, Any](
    trans,
    statsReceiver) {

  import Netty3.{fromNetty, newHandshaker, toNetty}
  import GenSerialClientDispatcher.wrapWriteException

  def this(trans: Transport[Any, Any]) =
    this(trans, NullStatsReceiver)

  private[this] def messages(): AsyncStream[Frame] =
    AsyncStream.fromFuture(trans.read()).flatMap {
      case _: CloseWebSocketFrame => AsyncStream.empty
      case frame => fromNetty(frame) +:: messages()
    }

  protected def dispatch(req: Request, p: Promise[Response]): Future[Unit] = {
    p.setValue(Response(messages))

    val handshake = newHandshaker(req.uri, req.headers)
    trans.write(handshake).rescue(wrapWriteException) before
      req.messages.foreachF(msg => trans.write(toNetty(msg))) before
        trans.write(new CloseWebSocketFrame)
  }
}
