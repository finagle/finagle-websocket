package com.twitter.finagle.websocket

import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.Service
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finagle.transport.Transport
import com.twitter.io.Buf
import com.twitter.util.{Closable, Future, Time}
import java.net.{SocketAddress, URI}
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import scala.collection.JavaConverters._

private[finagle] class ServerDispatcher(
    trans: Transport[Any, Any],
    service: Service[Request, Response],
    stats: StatsReceiver)
  extends Closable {

  import Netty3.{fromNetty, toNetty}

  private[this] def messages(): AsyncStream[Frame] =
    AsyncStream.fromFuture(trans.read()).flatMap {
      case _: CloseWebSocketFrame => AsyncStream.empty
      case frame => fromNetty(frame) +:: messages()
    }

  // The first item is a HttpRequest.
  trans.read().flatMap {
    case (req: HttpRequest, addr: SocketAddress) =>
      val uri = new URI(req.getUri)
      val headers = req.headers.asScala.map(e => e.getKey -> e.getValue).toMap
      service(Request(uri, headers, addr, messages)).flatMap { response =>
        response.messages
          .map(toNetty)
          .foreachF(trans.write)
          .ensure(trans.close())
      }

    case _ =>
      trans.close()
  }

  def close(deadline: Time): Future[Unit] = trans.close()
}
