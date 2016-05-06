package com.twitter.finagle

import com.twitter.finagle.netty3._
import com.twitter.finagle.param.{Label, ProtocolLibrary, Stats}
import com.twitter.finagle.client.{Transporter, StdStackClient, StackClient}
import com.twitter.finagle.server.{Listener, StdStackServer, StackServer}
import com.twitter.finagle.transport.Transport
import com.twitter.finagle.websocket.{Netty3, ClientDispatcher, Request, Response, ServerDispatcher}
import com.twitter.util.Closable
import java.net.SocketAddress
import org.jboss.netty.channel.Channel

object Websocket extends Server[Request, Response] {
  case class Client(
    stack: Stack[ServiceFactory[Request, Response]] = StackClient.newStack,
    params: Stack.Params = StackClient.defaultParams + ProtocolLibrary("ws"))
  extends StdStackClient[Request, Response, Client] {
    protected type In = Any
    protected type Out = Any

    protected def newTransporter(): Transporter[In, Out] =
      Netty3.newTransporter(params)

    protected def copy1(
      stack: Stack[ServiceFactory[Request, Response]] = this.stack,
      params: Stack.Params = this.params
    ): Client = copy(stack, params)

    protected def newDispatcher(transport: Transport[In, Out]): Service[Request, Response] =
      new ClientDispatcher(transport)

    def withTlsWithoutValidation: Client = withTransport.tlsWithoutValidation

    def withTls(hostname: String): Client = withTransport.tls(hostname)

    def withTls(cfg: Netty3TransporterTLSConfig): Client =
      configured(Transport.TLSClientEngine(Some(cfg.newEngine))
        ).configured(Transporter.TLSHostname(cfg.verifyHost))
  }

  val client: Client = Client()

  case class Server(
      stack: Stack[ServiceFactory[Request, Response]] = StackServer.newStack,
      params: Stack.Params = StackServer.defaultParams + ProtocolLibrary("ws"))
    extends StdStackServer[Request, Response, Server] {

    protected type In = Any
    protected type Out = Any

    protected def newListener(): Listener[In, Out] =
      Netty3.newListener(params)

    private[this] val statsReceiver = {
      val Stats(sr) = params[Stats]
      sr.scope("websocket")
    }

    protected def newDispatcher(
      transport: Transport[In, Out],
      service: Service[Request, Response]
    ): Closable =
        new ServerDispatcher(transport, service, statsReceiver)

    protected def copy1(
      stack: Stack[ServiceFactory[Request, Response]] = this.stack,
      params: Stack.Params = this.params
    ): Server = copy(stack, params)
  }

  val server: Websocket.Server = Server()

  def serve(
    addr: SocketAddress,
    factory: ServiceFactory[Request, Response]
  ): ListeningServer = server.serve(addr, factory)
}
