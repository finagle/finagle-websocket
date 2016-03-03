package com.twitter.finagle

import com.twitter.finagle.websocket._
import com.twitter.finagle.client._
import com.twitter.finagle.dispatch.{ SerialServerDispatcher, SerialClientDispatcher }
import com.twitter.finagle.netty3._
import com.twitter.finagle.param.{ ProtocolLibrary, Stats, Label }
import com.twitter.finagle.server._
import com.twitter.finagle.ssl.Ssl
import com.twitter.finagle.transport.Transport
import com.twitter.util.{ Duration, Future }
import java.net.{ InetSocketAddress, SocketAddress, URI }
import org.jboss.netty.channel.Channel

trait WebSocketRichClient { self: Client[WebSocketStart, WebSocket] =>
  def open(uri: String): Future[WebSocket] =
    open(new URI(uri))

  def open(uri: URI): Future[WebSocket] =
    open(WebSocketStart(uri))

  def open(uri: String, keepAlive: Duration): Future[WebSocket] =
    open(new URI(uri), keepAlive)

  def open(uri: URI, keepAlive: Duration): Future[WebSocket] =
    open(WebSocketStart(uri), Some(keepAlive))

  def open(start: WebSocketStart, keepAlive: Option[Duration] = None): Future[WebSocket] = {
    val addr = start.uri.getHost + ":" + start.uri.getPort

    var cli = HttpWebSocket.client

    if (start.uri.getScheme == "wss")
      cli = cli.withTlsWithoutValidation()

    var svc = cli.newClient(addr).toService
    keepAlive foreach { i => svc = KeepAlive(i) andThen svc }
    svc(start)
  }
}

object WebSocketClient {
  val stack: Stack[ServiceFactory[WebSocketStart, WebSocket]] =
    StackClient.newStack
}

case class WebSocketClient(
  stack: Stack[ServiceFactory[WebSocketStart, WebSocket]] = WebSocketClient.stack,
  params: Stack.Params = StackClient.defaultParams + ProtocolLibrary("websocket"))
extends StdStackClient[WebSocketStart, WebSocket, WebSocketClient] {
  protected type In = WebSocketStart
  protected type Out = WebSocket

  protected def newTransporter(): Transporter[WebSocketStart, WebSocket] = {
    val Label(label) = params[Label]
    val Stats(stats) = params[Stats]
    val codec = WebSocketCodec()
      .client(ClientCodecConfig(label))
    val newTransport = (ch: Channel) => codec.newClientTransport(ch, stats)

    Netty3Transporter(
      codec.pipelineFactory,
      params + Netty3Transporter.TransportFactory(newTransport))
  }

  protected def copy1(
    stack: Stack[ServiceFactory[WebSocketStart, WebSocket]] = this.stack,
    params: Stack.Params = this.params
  ): WebSocketClient = copy(stack, params)

  protected def newDispatcher(transport: Transport[WebSocketStart, WebSocket]): Service[WebSocketStart, WebSocket] =
    new SerialClientDispatcher(transport)

  def withTlsWithoutValidation(): WebSocketClient =
    configured(Transport.TLSClientEngine(Some({
      case inet: InetSocketAddress => Ssl.clientWithoutCertificateValidation(inet.getHostName, inet.getPort)
      case _ => Ssl.clientWithoutCertificateValidation()
    })))
}

object WebSocketServer {
  val stack: Stack[ServiceFactory[WebSocket, Unit]] =
    StackServer.newStack
}

case class WebSocketServer(
  stack: Stack[ServiceFactory[WebSocket, Unit]] = WebSocketServer.stack,
  params: Stack.Params = StackServer.defaultParams + ProtocolLibrary("websocket")
) extends StdStackServer[WebSocket, Unit, WebSocketServer] {
  protected type In = Unit
  protected type Out = WebSocket

  protected def newListener(): Listener[Unit, WebSocket] = {
    val Label(label) = params[Label]
    val pipeline = WebSocketCodec()
      .server(ServerCodecConfig(label, new SocketAddress {}))
      .pipelineFactory

    Netty3Listener(pipeline, params)
  }

  protected def newDispatcher(
    transport: Transport[Unit, WebSocket],
    service: Service[WebSocket, Unit]) = {
    val Stats(stats) = params[Stats]

    new SerialServerDispatcher(transport, service)
  }

  protected def copy1(
    stack: Stack[ServiceFactory[WebSocket, Unit]] = this.stack,
    params: Stack.Params = this.params
  ): WebSocketServer = copy(stack, params)

  def withTls(cfg: Netty3ListenerTLSConfig): WebSocketServer =
    configured(Transport.TLSServerEngine(Some(cfg.newEngine)))
}

object HttpWebSocket
extends Client[WebSocketStart, WebSocket]
with Server[WebSocket, Unit]
with WebSocketRichClient {
  val client = WebSocketClient().configured(Label("websocket"))
  val server = WebSocketServer().configured(Label("websocket"))

  def newClient(dest: Name, label: String) =
    client.newClient(dest, label)

  def newService(dest: Name, label: String) =
    client.newService(dest, label)

  def serve(addr: SocketAddress, service: ServiceFactory[WebSocket, Unit]): ListeningServer =
    server.serve(addr, service)
}
