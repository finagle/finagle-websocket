package com.twitter.finagle.websocket

import com.twitter.concurrent.{Offer, Broker}
import com.twitter.finagle.netty3.Conversions._
import com.twitter.util.{Promise, Return, Throw, Try}
import java.net.URI
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http.websocketx._
import org.jboss.netty.handler.codec.http.{HttpHeaders, HttpRequest, HttpResponse}
import scala.collection.JavaConversions._

class WebSocketHandler extends SimpleChannelHandler {
  protected[this] val messagesBroker = new Broker[String]
  protected[this] val binaryMessagesBroker = new Broker[Array[Byte]]
  protected[this] val closer = new Promise[Unit]

  protected[this] def write(
    ctx: ChannelHandlerContext,
    sock: WebSocket,
    ack: Option[Offer[Try[Unit]]] = None
  ) {
    def close() {
      sock.close()
      if (ctx.getChannel.isOpen) ctx.getChannel.close()
    }

    val awaitAck = ack match {
      // if we're awaiting an ack, don't offer to synchronize
      // on messages. thus we exert backpressure.
      case Some(ackOffer) =>
        ackOffer {
          case Return(_) => write(ctx, sock, None)
          case Throw(_) => close()
        }

      case None =>
        Offer.choose(
          sock.messages {
            message =>
              val frame = new TextWebSocketFrame(message)
              val writeFuture = Channels.future(ctx.getChannel)
              Channels.write(ctx, writeFuture, frame)
              write(ctx, sock, Some(writeFuture.toTwitterFuture.toOffer))
          },
          sock.binaryMessages {
            binary =>
              val frame = new BinaryWebSocketFrame(ChannelBuffers.wrappedBuffer(binary))
              val writeFuture = Channels.future(ctx.getChannel)
              Channels.write(ctx, writeFuture, frame)
              write(ctx, sock, Some(writeFuture.toTwitterFuture.toOffer))
          }
        )
    }
    awaitAck.sync()
  }

  override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    super.channelClosed(ctx, e)
    closer.setValue(())
  }
}

class WebSocketServerHandler extends WebSocketHandler {
  @volatile private[this] var handshaker: Option[WebSocketServerHandshaker] = None

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) = {
    e.getMessage match {
      case req: HttpRequest =>
        val location = "ws://" + req.headers.get(HttpHeaders.Names.HOST) + "/"
        val wsFactory = new WebSocketServerHandshakerFactory(location, null, false)
        handshaker = Option(wsFactory.newHandshaker(req))
        handshaker match {
          case None =>
            wsFactory.sendUnsupportedWebSocketVersionResponse(ctx.getChannel)
          case Some(h) =>
            h.handshake(ctx.getChannel, req)

            def close() { Channels.close(ctx.getChannel) }

            val webSocket = WebSocket(
              messages = messagesBroker.recv,
              binaryMessages = binaryMessagesBroker.recv,
              uri = new URI(req.getUri),
              headers = req.headers.map(e => e.getKey -> e.getValue).toMap,
              remoteAddress = ctx.getChannel.getRemoteAddress,
              onClose = closer,
              close = close)

            Channels.fireMessageReceived(ctx, webSocket)
        }

      case frame: CloseWebSocketFrame =>
        handshaker foreach { _.close(ctx.getChannel, frame) }

      case frame: PingWebSocketFrame =>
        ctx.getChannel.write(new PongWebSocketFrame(frame.getBinaryData))

      case frame: TextWebSocketFrame =>
        val ch = ctx.getChannel
        ch.setReadable(false)
        (messagesBroker ! frame.getText) ensure { ch.setReadable(true) }

      case frame: BinaryWebSocketFrame =>
        val ch = ctx.getChannel
        ch.setReadable(false)
        (binaryMessagesBroker ! frame.getBinaryData.array) ensure { ch.setReadable(true) }

      case invalid =>
        Channels.fireExceptionCaught(ctx,
          new IllegalArgumentException("invalid message \"%s\"".format(invalid)))
    }
  }

  override def writeRequested(ctx: ChannelHandlerContext, e: MessageEvent) = {
    e.getMessage match {
      case sock: WebSocket =>
        write(ctx, sock)

      case _: HttpResponse =>
        ctx.sendDownstream(e)

      case _: CloseWebSocketFrame =>
        ctx.sendDownstream(e)

      case invalid =>
        Channels.fireExceptionCaught(ctx,
          new IllegalArgumentException("invalid message \"%s\"".format(invalid)))
    }
  }
}

class WebSocketClientHandler extends WebSocketHandler {
  @volatile private[this] var handshaker: Option[WebSocketClientHandshaker] = None

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) = {
    e.getMessage match {
      case res: HttpResponse if handshaker.isDefined =>
        val hs = handshaker.get
        if (!hs.isHandshakeComplete)
          hs.finishHandshake(ctx.getChannel, res)

      case frame: CloseWebSocketFrame =>
        ctx.getChannel.close()

      case frame: PingWebSocketFrame =>
        ctx.getChannel.write(new PongWebSocketFrame(frame.getBinaryData))

      case frame: TextWebSocketFrame =>
        val ch = ctx.getChannel
        ch.setReadable(false)
        (messagesBroker ! frame.getText) ensure { ch.setReadable(true) }

      case frame: BinaryWebSocketFrame =>
        val ch = ctx.getChannel
        ch.setReadable(false)
        (binaryMessagesBroker ! frame.getBinaryData.array) ensure { ch.setReadable(true) }

      case invalid =>
        Channels.fireExceptionCaught(ctx,
          new IllegalArgumentException("invalid message \"%s\"".format(invalid)))
    }
  }


  override def writeRequested(ctx: ChannelHandlerContext, e: MessageEvent) = {
    e.getMessage match {
      case sock: WebSocket =>
        write(ctx, sock)

        def close() { Channels.close(ctx.getChannel) }

        val webSocket = sock.copy(
          messages = messagesBroker.recv,
          binaryMessages = binaryMessagesBroker.recv,
          onClose = closer,
          close = close)

        Channels.fireMessageReceived(ctx, webSocket)

        val wsFactory = new WebSocketClientHandshakerFactory
        val hs = wsFactory.newHandshaker(sock.uri, sock.version, null, false, sock.headers)
        handshaker = Some(hs)
        hs.handshake(ctx.getChannel) { _ => e.getFuture.setSuccess() }

      case _: HttpRequest =>
        ctx.sendDownstream(e)

      case _: CloseWebSocketFrame =>
        ctx.sendDownstream(e)

      case invalid =>
        Channels.fireExceptionCaught(ctx,
          new IllegalArgumentException("invalid message \"%s\"".format(invalid)))
    }
  }
}
