package com.twitter.finagle.irc

import com.twitter.finagle.irc.protocol.Message
import com.twitter.concurrent.{Offer, Broker}
import com.twitter.util.{Future, Promise, Return}
import org.jboss.netty.channel._

case class IrcHandle(
  messages: Offer[Message],
  onClose: Future[Unit],
  close: () => Unit
)

private[irc] abstract class Handler extends SimpleChannelHandler {
  private[this] val inbound = new Broker[Message]
  private[this] val onClose = new Promise[Unit]

  override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    onClose() = Return(())
  }

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) = {
    e.getMessage match {
      case buf: Message =>
        inbound ! buf
      case m =>
        val msg = "Unexpected message type sent upstream: %s".format(m.getClass.toString)
        throw new IllegalArgumentException(msg)
    }
  }

  override def writeRequested(ctx: ChannelHandlerContext, e: MessageEvent) = {
    e.getMessage match {
      case outbound: Offer[Message] =>
        e.getFuture.setSuccess()
        outbound foreach { message =>
          Channels.write(ctx, Channels.future(ctx.getChannel), message)
        }
      case m =>
        val msg = "Unexpected message type sent downstream: %s".format(m.getClass.toString)
        throw new IllegalArgumentException(msg)
    }
  }

  protected[this] def sendHandleUpstream(ctx: ChannelHandlerContext) {
    def close() { Channels.close(ctx.getChannel) }

    val streamHandle = IrcHandle(inbound.recv, onClose, close)
    Channels.fireMessageReceived(ctx, streamHandle)
  }
}

class ServerHandler extends Handler {
  override def channelConnected(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    super.channelConnected(ctx, e)
    sendHandleUpstream(ctx)
  }
}

class ClientHandler extends Handler {
  override def writeRequested(ctx: ChannelHandlerContext, e: MessageEvent) {
    super.writeRequested(ctx, e)
    sendHandleUpstream(ctx)
  }
}
