package com.twitter.finagle.websocket

import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.Service
import com.twitter.finagle.netty3.{BufChannelBuffer, ChannelBufferBuf}
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finagle.transport.Transport
import com.twitter.io.Buf
import com.twitter.util.{Closable, Future, Time}
import java.net.{SocketAddress, URI}
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.handler.codec.http.websocketx._
import scala.collection.JavaConverters._

private[finagle] class ServerDispatcher(
    trans: Transport[Any, Any],
    service: Service[Request, Response],
    stats: StatsReceiver)
  extends Closable {

  import ServerDispatcher._

  private[this] def messages(): AsyncStream[Frame] =
    AsyncStream.fromFuture(trans.read().map(fromNetty)) ++ messages()

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

private object ServerDispatcher {
  import Frame._

  def fromNetty(m: Any): Frame = m match {
    case text: TextWebSocketFrame =>
      Text(text.getText)

    case cont: ContinuationWebSocketFrame =>
      Text(cont.getText)

    case bin: BinaryWebSocketFrame =>
      Binary(new ChannelBufferBuf(bin.getBinaryData))

    case ping: PingWebSocketFrame =>
      Ping(new ChannelBufferBuf(ping.getBinaryData))

    case pong: PongWebSocketFrame =>
      Pong(new ChannelBufferBuf(pong.getBinaryData))

    case frame =>
      throw new IllegalStateException(s"unknown frame: $frame")
  }

  def toNetty(frame: Frame): WebSocketFrame = frame match {
    case Text(message) =>
      new TextWebSocketFrame(message)

    case Binary(buf) =>
      new BinaryWebSocketFrame(BufChannelBuffer(buf))

    case Ping(buf) =>
      new PingWebSocketFrame(BufChannelBuffer(buf))

    case Pong(buf) =>
      new PongWebSocketFrame(BufChannelBuffer(buf))
  }
}
