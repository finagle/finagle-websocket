package com.twitter.finagle.websocket

import com.twitter.concurrent.AsyncStream
import com.twitter.util.{ Closable, Future, Time }
import com.twitter.io.Buf
import java.net.SocketAddress
import java.net.URI
import org.jboss.netty.handler.codec.http.websocketx.WebSocketVersion

case class WebSocketStart(
  uri: URI,
  headers: Map[String, String] = Map.empty[String, String],
  version: WebSocketVersion = WebSocketVersion.V13)

abstract class WebSocket(
  val uri: URI,
  val headers: Map[String, String],
  val remoteAddress: SocketAddress,
  val version: WebSocketVersion,
  val closed: Future[Unit]
) extends Closable {

  def close(deadline: Time): Future[Unit]

  def write(msg: WebSocketFrame): Future[Unit]

  def write(str: String): Future[Unit] =
    write(WebSocketFrame.Text(str))

  def write(buf: Buf): Future[Unit] =
    write(WebSocketFrame.Binary(buf))

  def write(bytes: Array[Byte]): Future[Unit] =
    write(Buf.ByteArray.Shared(bytes))

  def stream: AsyncStream[WebSocketFrame]
}
