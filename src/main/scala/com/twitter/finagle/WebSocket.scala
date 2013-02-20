package com.twitter.finagle

import com.twitter.finagle.websocket.{WebSocket => WS}
import com.twitter.finagle.client._
import com.twitter.finagle.dispatch.{SerialServerDispatcher, SerialClientDispatcher}
import com.twitter.finagle.netty3._
import com.twitter.finagle.server._
import java.net.SocketAddress

object WebSocketBinder extends DefaultBinder[WS, WS, WS, WS](
  new Netty3Transporter(websocket.WebSocketCodec().client(ClientCodecConfig("websocketclient")).pipelineFactory),
  new SerialClientDispatcher(_)
)

object WebSocketClient extends DefaultClient[WS, WS](
  WebSocketBinder, DefaultPool[WS, WS]()
)

object WebSocketListener extends Netty3Listener[WS, WS](
  websocket.WebSocketCodec().server(ServerCodecConfig("websocketserver", new SocketAddress{})).pipelineFactory
)

object WebSocketServer extends DefaultServer[WS, WS, WS, WS](
  WebSocketListener, new SerialServerDispatcher(_, _)
)

object HttpWebSocket extends Client[WS, WS] with Server[WS, WS] {
  def newClient(group: Group[SocketAddress]): ServiceFactory[WS, WS] =
    WebSocketClient.newClient(group)

  def serve(addr: SocketAddress, service: ServiceFactory[WS, WS]): ListeningServer =
    WebSocketServer.serve(addr, service)
}
