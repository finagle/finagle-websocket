package com.twitter.finagle

import com.twitter.finagle.param.{Label, ProtocolLibrary, Stats}
import com.twitter.finagle.server.{Listener, StdStackServer, StackServer}
import com.twitter.finagle.transport.Transport
import com.twitter.finagle.websocket.{Netty3, Request, Response, ServerDispatcher}
import com.twitter.util.Closable
import java.net.SocketAddress

object Websocket extends Server[Request, Response] {
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
