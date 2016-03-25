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

class WebSocketHandler extends SimpleChannelHandler {
  protected[this] val messagesBroker = new Broker[String]
  protected[this] val binaryMessagesBroker = new Broker[Array[Byte]]
  protected[this] val closer = new Promise[Unit]
  protected[this] val timer = DefaultTimer.twitter

  private[this] class ListenerImpl(promise:Promise[Unit]) extends ChannelFutureListener {
    def operationComplete(cf: ChannelFuture) {
      if(cf.isSuccess)
        promise.setValue(())
      else if(cf.isCancelled)
        promise.setException(new CancelledRequestException)
      else
        promise.setException(cf.getCause)
    }
  }

  protected[this] def channelFutureToOffer(future: ChannelFuture): Offer[Try[Unit]] = {
    val promise = new Promise[Unit]

    future.addListener(new ListenerImpl(promise))

    promise.toOffer
  }

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
              write(ctx, sock, Some(channelFutureToOffer(writeFuture)))
          },
          sock.binaryMessages {
            binary =>
              val frame = new BinaryWebSocketFrame(ChannelBuffers.wrappedBuffer(binary))
              val writeFuture = Channels.future(ctx.getChannel)
              Channels.write(ctx, writeFuture, frame)
              write(ctx, sock, Some(channelFutureToOffer(writeFuture)))
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
        handshaker.foreach(_.close(ctx.getChannel, frame))

      case frame: WebSocketFrame =>
        Channels.fireMessageReceived(ctx, frame)

      case invalid =>
        Channels.fireExceptionCaught(ctx,
          new IllegalArgumentException(s"invalid message: $invalid"))
    }
}

class WebSocketClientHandler extends WebSocketHandler {
  @volatile private[this] var handshaker: Option[WebSocketClientHandshaker] = None
  private[this] var keepAliveTask: Option[TimerTask] = None

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

      case frame: PongWebSocketFrame =>
        // do nothing

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

        def close() {
          keepAliveTask.foreach(_.close())
          Channels.close(ctx.getChannel)
        }

        val webSocket = sock.copy(
          messages = messagesBroker.recv,
          binaryMessages = binaryMessagesBroker.recv,
          onClose = closer,
          close = close)

        Channels.fireMessageReceived(ctx, webSocket)

        val wsFactory = new WebSocketClientHandshakerFactory
        val hs = wsFactory.newHandshaker(sock.uri, sock.version, null, false, sock.headers)
        handshaker = Some(hs)
        hs.handshake(ctx.getChannel).addListener(new ChannelFutureListener {
          override def operationComplete(f:ChannelFuture) {

            // initiate the keep-alive
            for(interval <- sock.keepAlive) {
              timer.schedule(interval) {
                ctx.getChannel.write(new PingWebSocketFrame())
              }

            }

            e.getFuture.setSuccess()
          }
        })

      case _: HttpRequest =>
        ctx.sendDownstream(e)

      case _: CloseWebSocketFrame =>
        ctx.sendDownstream(e)

      case _: PingWebSocketFrame =>
        ctx.sendDownstream(e)

      case invalid =>
        Channels.fireExceptionCaught(ctx,
          new IllegalArgumentException("invalid message \"%s\"".format(invalid)))
    }
  }
}
