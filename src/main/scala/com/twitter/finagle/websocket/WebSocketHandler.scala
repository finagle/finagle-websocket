package com.twitter.finagle.websocket

import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http.websocketx._
import org.jboss.netty.handler.codec.http.{
  HttpHeaders, HttpRequest, HttpResponse, HttpResponseEncoder}
import com.twitter.finagle.channel.BrokerChannelHandler
import com.twitter.finagle.netty3.Conversions._
import com.twitter.finagle.netty3.{Cancelled, Ok, Error}
import com.twitter.concurrent.{Offer, Broker}
import com.twitter.util.{Try, Return, Throw}

class WebSocketHandler(path: String = "/") extends BrokerChannelHandler {
  @volatile private[this] var handshaker: Option[WebSocketServerHandshaker] = None

  private[this] val messagesBroker = new Broker[String]
  private[this] val closeBroker = new Broker[Unit]
  private[this] val close = closeBroker.recv

  private[this] val webSocket = new WebSocket {
    val messages = messagesBroker.recv
    def release() { closeBroker ! () }
  }

  private[this] def write(
    ctx: ChannelHandlerContext,
    sock: WebSocket,
    ack: Option[Offer[Try[Unit]]] = None
  ) {
    def close() {
      sock.release()
      if (ctx.getChannel.isOpen) ctx.getChannel.close()
      upstreamEvent foreach {
        case Message(_, _) => /* drop */
        case e => e.sendUpstream()
      }
    }

    val awaitAck = ack match {
      // if we're awaiting an ack, don't offer to synchronize
      // on messages. thus we exert backpressure.
      case Some(ack) =>
        ack {
          case Return(_) => write(ctx, sock, None)
          case Throw(_) => close()
        }

      case None =>
        sock.messages { message =>
          val frame = new TextWebSocketFrame(message)
          val writeFuture = Channels.future(ctx.getChannel)
          Channels.write(ctx, writeFuture, frame)
          write(ctx, sock, Some(writeFuture.toTwitterFuture.toOffer))
        }
    }
    awaitAck.sync()
  }

  private[this] def awaitEvent(dead: Boolean) {
    Offer.select(
      upstreamEvent {
        case MessageValue(req: HttpRequest, ctx) =>
          val location = "ws://" + req.getHeader(HttpHeaders.Names.HOST) + path
          val wsFactory = new WebSocketServerHandshakerFactory(location, null, false)
          handshaker = Option(wsFactory.newHandshaker(req))
          handshaker match {
            case None =>
              wsFactory.sendUnsupportedWebSocketVersionResponse(ctx.getChannel())
            case Some(h) =>
              // This is an ugly little hack to ensure we can process outgoing HttpResponses.
              // The handshake replaces the HttpResponseEncoder with WebSocketFrameEncoder but
              // the nature of Offer/Brokers is that the message wont have gone out yet. This
              // puts a shim in place until the message has been processed.
              val channel = ctx.getChannel
              val pipeline = channel.getPipeline
              h.handshake(channel, req) { _ => pipeline.remove("shim") }
              pipeline.addFirst("shim", new HttpResponseEncoder)

              Channels.fireMessageReceived(ctx, webSocket)
          }
          awaitEvent(dead)

        case MessageValue(frame: CloseWebSocketFrame, ctx) =>
          handshaker foreach { _.close(ctx.getChannel(), frame) }
          awaitEvent(dead)

        case MessageValue(frame: PingWebSocketFrame, ctx) =>
          ctx.getChannel().write(new PongWebSocketFrame(frame.getBinaryData()))
          awaitEvent(dead)

        case MessageValue(frame: TextWebSocketFrame, ctx) if !dead =>
          val ch = ctx.getChannel
          ch.setReadable(false)
          (messagesBroker ! frame.getText) ensure {
            ch.setReadable(true)
            awaitEvent(dead)
          }

        case MessageValue(frame: WebSocketFrame, _) if dead =>
          awaitEvent(dead)

        case MessageValue(invalid, ctx) =>
          Channels.fireExceptionCaught(ctx,
            new IllegalArgumentException(
              "invalid message \"%s\"".format(invalid)))
          awaitEvent(dead)

        case e@(Closed(_, _) | Disconnected(_, _)) =>
          e.sendUpstream()
          awaitEvent(true)
          // send close

        case e@Exception(exc, ctx) =>
          e.sendUpstream()
          awaitEvent(dead)
          // send exception

        case e =>
          e.sendUpstream()
          awaitEvent(dead)
      },

      downstreamEvent {
        case WriteValue(sock: WebSocket, ctx) if !dead =>
          write(ctx, sock)
          awaitEvent(dead)

        case WriteValue(sock: WebSocket, _) if dead =>
          sock.release()
          awaitEvent(dead)

        case e@WriteValue(_: HttpResponse, _) =>
          e.sendDownstream()
          awaitEvent(dead)

        case e@WriteValue(_: CloseWebSocketFrame, _) =>
          e.sendDownstream()
          awaitEvent(dead)

        case WriteValue(invalid, ctx) =>
          Channels.fireExceptionCaught(ctx,
            new IllegalArgumentException(
              "Invalid reply \"%s\"".format(invalid)))
          awaitEvent(dead)

        case e@Close(_, _) =>
          e.sendDownstream()
          awaitEvent(true)

        case e =>
          e.sendDownstream()
          awaitEvent(dead)
      }
    )
  }

  awaitEvent(false)
}
