package com.twitter.finagle

import com.twitter.finagle.websocket.{WebSocket, WebSocketCodec}
import com.twitter.finagle.client._
import com.twitter.finagle.dispatch.{SerialServerDispatcher, SerialClientDispatcher}
import com.twitter.finagle.netty3._
import com.twitter.finagle.server._
import com.twitter.concurrent.Offer
import com.twitter.util.Future
import java.net.{SocketAddress, URI}

trait WebSocketRichClient {
  def open(out: Offer[String], uri: String): Future[WebSocket] =
    open(out, new URI(uri))

  def open(out: Offer[String], uri: URI): Future[WebSocket] = {
    val socket = WebSocket(messages = out, uri = uri)
    val addr = uri.getHost + ":" + uri.getPort
    HttpWebSocket.newClient(addr).toService(socket)
  }
}

object WebSocketTransporter extends Netty3Transporter[WebSocket, WebSocket](
  "websocket", WebSocketCodec().client(ClientCodecConfig("websocketclient")).pipelineFactory
)

object WebSocketClient extends DefaultClient[WebSocket, WebSocket](
  name = "websocket",
  endpointer = Bridge[WebSocket, WebSocket, WebSocket, WebSocket](
    WebSocketTransporter, new SerialClientDispatcher(_)),
  pool = DefaultPool[WebSocket, WebSocket]()
)

object WebSocketListener extends Netty3Listener[WebSocket, WebSocket](
  "websocket", WebSocketCodec().server(ServerCodecConfig("websocketserver", new SocketAddress{})).pipelineFactory
)

object WebSocketServer extends DefaultServer[WebSocket, WebSocket, WebSocket, WebSocket](
  "websocketsrv", WebSocketListener, new SerialServerDispatcher(_, _)
)

object HttpWebSocket
  extends Client[WebSocket, WebSocket]
  with Server[WebSocket, WebSocket]
  with WebSocketRichClient
{
  def newClient(dest: Name, label: String): ServiceFactory[WebSocket, WebSocket] =
    WebSocketClient.newClient(dest, label)

  def serve(addr: SocketAddress, service: ServiceFactory[WebSocket, WebSocket]): ListeningServer =
    WebSocketServer.serve(addr, service)
}
