package com.twitter.finagle

import com.twitter.finagle.irc._
import com.twitter.finagle.client._
import com.twitter.finagle.dispatch.{SerialServerDispatcher, SerialClientDispatcher}
import com.twitter.finagle.netty3._
import com.twitter.finagle.server._
import com.twitter.util.Future
import com.twitter.concurrent.{Broker, Offer}
import java.net.SocketAddress

object IrcTransporter extends Netty3Transporter[IrcHandle, Offer[Message]](
  "irc", IrcCodec.client(ClientCodecConfig("ircclient")).pipelineFactory
)

object IrcClient extends DefaultClient[IrcHandle, Offer[Message]](
  name = "irc",
  endpointer = Bridge[IrcHandle, Offer[Message], IrcHandle, Offer[Message]](
    IrcTransporter, new SerialClientDispatcher(_)),
  pool = DefaultPool[IrcHandle, Offer[Message]]()
)

object IrcListener extends Netty3Listener[Offer[Message], IrcHandle](
  "irc", IrcCodec.server(ServerCodecConfig("ircserver", new SocketAddress{})).pipelineFactory
)

object IrcServer extends DefaultServer[IrcHandle, Offer[Message], Offer[Message], IrcHandle](
  "ircsrv", IrcListener, new SerialServerDispatcher(_, _)
)

object Irc
  extends Client[IrcHandle, Offer[Message]]
  with Server[IrcHandle, Offer[Message]]
{
  def newClient(group: Group[SocketAddress]): ServiceFactory[IrcHandle, Offer[Message]] =
    IrcClient.newClient(group)

  def serve(addr: SocketAddress, service: ServiceFactory[IrcHandle, Offer[Message]]): ListeningServer =
    IrcServer.serve(addr, service)
}

object Main extends App {
  val server = Irc.serve(":6060", new com.twitter.finagle.Service[IrcHandle, Offer[Message]] {
    def apply(handle: IrcHandle): Future[Offer[Message]] = {
      val outgoing = new Broker[Message]
      handle.messages foreach { msg =>
        println(msg)
        msg match {
          case ChanList(_, _) =>
          case _ => outgoing ! ErrUknownCommand("this", "you", msg.encode)
        }
      }
      Future.value(outgoing.recv)
    }
  })

  com.twitter.util.Await.ready(server)
}
