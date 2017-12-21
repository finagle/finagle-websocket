package com.twitter.finagle

import com.twitter.finagle.netty3._
import com.twitter.finagle.param.{Label, ProtocolLibrary, Stats}
import com.twitter.finagle.client.{StackClient, StdStackClient, Transporter}
import com.twitter.finagle.server.{Listener, StackServer, StdStackServer}
import com.twitter.finagle.websocket.{ClientDispatcher, Netty3, Request, Response, ServerDispatcher}
import com.twitter.finagle.transport.{Transport, TransportContext}
import com.twitter.util.Closable
import java.net.SocketAddress

import com.twitter.finagle.ssl.client.SslClientConfiguration
import org.jboss.netty.channel.Channel

object Websocket extends Server[Request, Response] {
  case class Client(stack: Stack[ServiceFactory[Request, Response]] = StackClient.newStack,
                    params: Stack.Params = StackClient.defaultParams + ProtocolLibrary("ws"))
  extends StdStackClient[Request, Response, Client] {
    override protected type In = Any
    override protected type Out = Any
    override protected type Context = TransportContext

    override protected def newTransporter(addr: SocketAddress): Transporter[In, Out, Context] =
      Netty3.newTransporter(addr, params)

    override protected def copy1(
      stack: Stack[ServiceFactory[Request, Response]] = this.stack,
      params: Stack.Params = this.params
    ): Client = copy(stack, params)

    override protected def newDispatcher(transport: Transport[In, Out] {
      type Context <: TransportContext
    }): Service[Request, Response] =
      new ClientDispatcher(transport)

    def withTlsWithoutValidation: Client = withTransport.tlsWithoutValidation

    def withTls(hostname: String): Client = withTransport.tls(hostname)

    def withTls(cfg: SslClientConfiguration): Client =
      withTransport.tls.configured(Transport.ClientSsl(Some(cfg)))
  }

  val client: Client = Client()

  case class Server(
      stack: Stack[ServiceFactory[Request, Response]] = StackServer.newStack,
      params: Stack.Params = StackServer.defaultParams + ProtocolLibrary("ws"))
    extends StdStackServer[Request, Response, Server] {

    protected type In = Any
    protected type Out = Any
    protected type Context = TransportContext

    protected def newListener(): Listener[In, Out, Context] =
      Netty3.newListener(params)

    private[this] val statsReceiver = {
      val Stats(sr) = params[Stats]
      sr.scope("websocket")
    }

    protected def newDispatcher(
      transport: Transport[In, Out] { type Context <: Server.this.Context },
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
