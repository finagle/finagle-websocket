package com.twitter.finagle

import com.twitter.finagle.irc._
import com.twitter.finagle.irc.protocol._
import com.twitter.finagle.client._
import com.twitter.finagle.netty3._
import com.twitter.finagle.server._
import com.twitter.finagle.transport.Transport
import com.twitter.util.Future
import com.twitter.concurrent.{Broker, Offer}
import java.net.SocketAddress
import org.jboss.netty.channel.{Channels, ChannelPipelineFactory}
import org.jboss.netty.buffer.ChannelBuffer

object PipelineFactory extends ChannelPipelineFactory {
  def getPipeline = Channels.pipeline()
}

object NettyTrans extends Netty3Transporter[ChannelBuffer, ChannelBuffer](
  "irc", PipelineFactory)

object IrcClient extends DefaultClient[Offer[Message], IrcHandle](
  name = "irc",
  endpointer = Bridge[Message, Message,  Offer[Message], IrcHandle](
    NettyTrans(_, _) map { IrcTransport(_, DefaultIrcDecoder) }, new ClientDispatcher(_)))

object IrcListener extends Listener[Message, Message] {
  private[this] val nettyListener = Netty3Listener[ChannelBuffer, ChannelBuffer]("irc", PipelineFactory)
  def listen(addr: SocketAddress)(serveTransport: Transport[Message, Message] => Unit): ListeningServer =
    nettyListener.listen(addr) { t => serveTransport(IrcTransport(t)) }
}

object IrcServer extends DefaultServer[IrcHandle, Offer[Message], Message, Message](
  "ircsrv", IrcListener, new ServerDispatcher(_, _))

object Irc
extends Client[Offer[Message], IrcHandle]
with Server[IrcHandle, Offer[Message]] {
  def newClient(name: Name, label: String): ServiceFactory[Offer[Message], IrcHandle] =
    IrcClient.newClient(name, label)

  def serve(addr: SocketAddress, service: ServiceFactory[IrcHandle, Offer[Message]]): ListeningServer =
    IrcServer.serve(addr, service)
}
