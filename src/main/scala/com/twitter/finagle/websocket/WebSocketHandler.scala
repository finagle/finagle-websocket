package com.twitter.finagle.websocket

import com.twitter.concurrent.{Offer, Broker}
import com.twitter.finagle.{CancelledRequestException, ChannelException}
import com.twitter.finagle.util.DefaultTimer
import com.twitter.util.{Future, Promise, Return, Throw, Try, TimerTask}
import java.net.URI
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http.websocketx._
import org.jboss.netty.handler.codec.http.{HttpHeaders, HttpRequest, HttpResponse}
import scala.collection.JavaConversions._

private[finagle] class WebSocketServerHandler extends SimpleChannelUpstreamHandler {
  private[this] var handshaker: Option[WebSocketServerHandshaker] = None

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) =
    e.getMessage match {
      case req: HttpRequest =>
        val scheme = if(req.getUri.startsWith("wss")) "wss" else "ws"
        val location = scheme + "://" + req.headers.get(HttpHeaders.Names.HOST) + "/"
        val wsFactory = new WebSocketServerHandshakerFactory(location, null, false)
        handshaker = Option(wsFactory.newHandshaker(req))
        handshaker match {
          case None =>
            wsFactory.sendUnsupportedWebSocketVersionResponse(ctx.getChannel)
          case Some(ref) =>
            ref.handshake(ctx.getChannel, req)
            val addr = ctx.getChannel.getRemoteAddress
            Channels.fireMessageReceived(ctx, (req, addr))
        }

      case frame: CloseWebSocketFrame =>
        handshaker match {
          case Some(hs) =>
            hs.close(ctx.getChannel, frame).addListener(ChannelFutureListener.CLOSE)
            Channels.fireMessageReceived(ctx, frame)

          case None =>
            Channels.fireExceptionCaught(ctx,
              new IllegalArgumentException(s"Close received before handshake"))
        }

      case frame: WebSocketFrame =>
        Channels.fireMessageReceived(ctx, frame)

      case invalid =>
        Channels.fireExceptionCaught(ctx,
          new IllegalArgumentException(s"invalid message: $invalid"))
    }
}

private[finagle] class WebSocketClientHandler extends SimpleChannelHandler {
  @volatile private[this] var ref: Option[WebSocketClientHandshaker] = None

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent): Unit =
    e.getMessage match {
      case res: HttpResponse =>
        ref match {
          case None =>
            throw new IllegalStateException("unexpected HTTP response before handshake")
          case Some(handshaker) if handshaker.isHandshakeComplete =>
            throw new IllegalStateException("unexpected HTTP response after handshake")
          case Some(handshaker) =>
            handshaker.finishHandshake(ctx.getChannel, res)
        }

      case frame: WebSocketFrame =>
        Channels.fireMessageReceived(ctx, frame)

      case invalid =>
        Channels.fireExceptionCaught(ctx,
          new IllegalArgumentException(s"invalid message: $invalid"))
    }

  override def writeRequested(ctx: ChannelHandlerContext, e: MessageEvent): Unit =
    e.getMessage match {
      case handshaker: WebSocketClientHandshaker =>
        ref = Some(handshaker)
        val future = handshaker.handshake(ctx.getChannel)
        future.addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
        future.addListener(new ChannelFutureListener {
          override def operationComplete(f: ChannelFuture): Unit =
            if (f.isSuccess) e.getFuture.setSuccess()
            else if (f.isCancelled) e.getFuture.cancel()
            else e.getFuture.setFailure(f.getCause)
        })

      case frame: WebSocketFrame =>
        ctx.sendDownstream(e)

      case req: HttpRequest if !ref.isEmpty =>
        ctx.sendDownstream(e)

      case invalid =>
        Channels.fireExceptionCaught(ctx,
          new IllegalArgumentException(s"invalid message: $invalid"))
    }
}
