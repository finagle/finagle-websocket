package com.twitter.finagle

import com.twitter.finagle.websocket.{WebSocket, WebSocketCodec}
import com.twitter.finagle.client._
import com.twitter.finagle.dispatch.{SerialServerDispatcher, SerialClientDispatcher}
import com.twitter.finagle.netty3._
import com.twitter.finagle.param.{ProtocolLibrary, Stats, Label}
import com.twitter.finagle.server._
import com.twitter.finagle.ssl.Ssl
import com.twitter.finagle.transport.Transport
import com.twitter.concurrent.Offer
import com.twitter.util.{Duration, Future}
import java.net.{InetSocketAddress, SocketAddress, URI}
import org.jboss.netty.channel.Channel

trait WebSocketRichClient { self: Client[WebSocket, WebSocket] =>
  def open(out: Offer[String], uri: String): Future[WebSocket] =
    open(out, Offer.never, new URI(uri))

  def open(out: Offer[String], uri: URI): Future[WebSocket] =
    open(out, Offer.never, uri)

  def open(out: Offer[String], binaryOut: Offer[Array[Byte]], uri: String): Future[WebSocket] =
    open(out, binaryOut, new URI(uri))

  def open(out: Offer[String], binaryOut: Offer[Array[Byte]], uri: String, keepAlive: Option[Duration]): Future[WebSocket] =
    open(out, binaryOut, new URI(uri), keepAlive = keepAlive)

  def open(out: Offer[String], binaryOut: Offer[Array[Byte]], uri: URI, keepAlive: Option[Duration] = None): Future[WebSocket] = {
    val socket = WebSocket(
      messages = out,
      binaryMessages = binaryOut,
      uri = uri,
      keepAlive = keepAlive)
    val addr = uri.getHost + ":" + uri.getPort

    var cli = HttpWebSocket.client

    if(uri.getScheme == "wss")
      cli = cli.withTlsWithoutValidation()

    cli.newClient(addr).toService(socket)
  }
}

object WebSocketClient {
  val stack: Stack[ServiceFactory[WebSocket, WebSocket]] =
    StackClient.newStack
}

case class WebSocketClient(
  stack: Stack[ServiceFactory[WebSocket, WebSocket]] = WebSocketClient.stack,
  params: Stack.Params = StackClient.defaultParams + ProtocolLibrary("websocket"))
extends StdStackClient[WebSocket, WebSocket, WebSocketClient] {
  protected type In = WebSocket
  protected type Out = WebSocket

  protected def newTransporter(): Transporter[WebSocket, WebSocket] = {
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
    stack: Stack[ServiceFactory[WebSocket, WebSocket]] = this.stack,
    params: Stack.Params = this.params
  ): WebSocketClient = copy(stack, params)

  protected def newDispatcher(transport: Transport[WebSocket, WebSocket]): Service[WebSocket, WebSocket] =
    new SerialClientDispatcher(transport)

  def withTlsWithoutValidation(): WebSocketClient =
    configured(Transport.TLSClientEngine(Some({
      case inet: InetSocketAddress => Ssl.clientWithoutCertificateValidation(inet.getHostName, inet.getPort)
      case _ => Ssl.clientWithoutCertificateValidation()
    })))
}

object WebSocketServer {
  val stack: Stack[ServiceFactory[WebSocket, WebSocket]] =
    StackServer.newStack
}

case class WebSocketServer(
  stack: Stack[ServiceFactory[WebSocket, WebSocket]] = WebSocketServer.stack,
  params: Stack.Params = StackServer.defaultParams + ProtocolLibrary("websocket")
) extends StdStackServer[WebSocket, WebSocket, WebSocketServer] {
  protected type In = WebSocket
  protected type Out = WebSocket

  protected def newListener(): Listener[WebSocket, WebSocket] = {
    val Label(label) = params[Label]
    val wsPipeline = WebSocketCodec()
      .server(ServerCodecConfig(label, new SocketAddress{}))
      .pipelineFactory

    Netty3Listener(wsPipeline, params)
  }

  protected def newDispatcher(
    transport: Transport[WebSocket, WebSocket],
    service: Service[WebSocket, WebSocket]) = {
    val Stats(stats) = params[Stats]

    new SerialServerDispatcher(transport, service)
  }

  protected def copy1(
    stack: Stack[ServiceFactory[WebSocket, WebSocket]] = this.stack,
    params: Stack.Params = this.params
  ): WebSocketServer = copy(stack, params)

  def withTls(cfg: Netty3ListenerTLSConfig): WebSocketServer =
    configured(Transport.TLSServerEngine(Some(cfg.newEngine)))
}

object HttpWebSocket
extends Client[WebSocket, WebSocket]
with Server[WebSocket, WebSocket]
with WebSocketRichClient {
  val client = WebSocketClient().configured(Label("websocket"))
  val server = WebSocketServer().configured(Label("websocket"))

  def newClient(dest: Name, label: String) =
    client.newClient(dest, label)

  def newService(dest: Name, label: String) =
    client.newService(dest, label)

  def serve(addr: SocketAddress, service: ServiceFactory[WebSocket, WebSocket]): ListeningServer =
    server.serve(addr, service)
}
