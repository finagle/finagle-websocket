package com.twitter.finagle.websocket

import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.CancelledRequestException
import com.twitter.finagle.netty3.{ BufChannelBuffer, ChannelBufferBuf }
import com.twitter.finagle.util.DefaultTimer
import com.twitter.io.{ Buf, Reader }
import com.twitter.util._
import java.net.URI
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http.websocketx._
import org.jboss.netty.handler.codec.http.{ HttpHeaders, HttpRequest, HttpResponse }
import scala.collection.JavaConverters._
import scala.language.implicitConversions

class FutureOps(cf: ChannelFuture) {
  def toFuture: Future[Unit] = {
    val p = Promise[Unit]
    cf.addListener(new ChannelFutureListener {
      def operationComplete(f: ChannelFuture): Unit = {
        if (f.isSuccess) {
          p.setDone
        } else if (f.isCancelled) {
          p.setException(new CancelledRequestException)
        } else {
          p.setException(f.getCause)
        }
      }
    })
    p
  }
}

sealed abstract class WebSocketHandler extends SimpleChannelHandler {
  implicit def futureOps(cf: ChannelFuture): FutureOps =
    new FutureOps(cf)

  protected val closed = new Promise[Unit]
  protected val readerWriter = Reader.writable()
  protected val timer = DefaultTimer.twitter

  protected def writeMsg(ctx: ChannelHandlerContext, msg: WebSocketFrame): Future[Unit] =
    msg match {
      case WebSocketFrame.Text(str) =>
        Channels.write(ctx.getChannel, new TextWebSocketFrame(str)).toFuture
      case WebSocketFrame.Binary(buf) =>
        Channels.write(ctx.getChannel, new BinaryWebSocketFrame(BufChannelBuffer(buf))).toFuture
      case WebSocketFrame.Ping(data) =>
        Channels.write(ctx.getChannel, new PingWebSocketFrame(BufChannelBuffer(data))).toFuture
      case WebSocketFrame.Pong(data) =>
        Channels.write(ctx.getChannel, new PongWebSocketFrame(BufChannelBuffer(data))).toFuture
      case f: WebSocketFrame.Close =>
        Channels.write(ctx.getChannel, new CloseWebSocketFrame(f.code, f.reason.orNull)).toFuture
    }

  protected def streamOut(ctx: ChannelHandlerContext, frame: WebSocketFrame): Future[Unit] = {
    val ch = ctx.getChannel
    ch.setReadable(false)
    readerWriter.write(frame) ensure { ch.setReadable(true) }
  }

  override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    super.channelClosed(ctx, e)
    readerWriter.discard()
    closed.setDone()
  }

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) =
    (receiveMsg orElse recv orElse invalidMsg)((ctx, e, e.getMessage))

  override def writeRequested(ctx: ChannelHandlerContext, e: MessageEvent) =
    (requestWrite orElse send orElse invalidMsg)((ctx, e, e.getMessage))

  protected def handleClose(ctx: ChannelHandlerContext, frame: CloseWebSocketFrame): Unit
  protected def receiveMsg: PartialFunction[(ChannelHandlerContext, MessageEvent, Object), Unit]
  protected def requestWrite: PartialFunction[(ChannelHandlerContext, MessageEvent, Object), Unit]

  private val invalidMsg: PartialFunction[(ChannelHandlerContext, MessageEvent, Object), Unit] = {
    case (ctx, e, invalid) =>
      Channels.fireExceptionCaught(ctx,
        new IllegalArgumentException("invalid message \"%s\"".format(invalid)))
  }

  private val recv: PartialFunction[(ChannelHandlerContext, MessageEvent, Object), Unit] = {
    case (ctx, _, frame: CloseWebSocketFrame) =>
      frame.getStatusCode match {
        case 1000 => streamOut(ctx, WebSocketFrame.NormalClose(Option(frame.getReasonText)))
        case 1001 => streamOut(ctx, WebSocketFrame.GoingAway(Option(frame.getReasonText)))
        case 1002 => streamOut(ctx, WebSocketFrame.Error(Option(frame.getReasonText)))
        case 1003 => streamOut(ctx, WebSocketFrame.Invalid(Option(frame.getReasonText)))
        case _ =>
      }
      handleClose(ctx, frame)

    case (ctx, _, frame: PingWebSocketFrame) =>
      streamOut(ctx, WebSocketFrame.Ping(ChannelBufferBuf.Shared(frame.getBinaryData)))

    case (ctx, _, frame: PongWebSocketFrame) =>
      streamOut(ctx, WebSocketFrame.Pong(ChannelBufferBuf.Shared(frame.getBinaryData)))

    case (ctx, _, frame: TextWebSocketFrame) =>
      streamOut(ctx, WebSocketFrame.Text(frame.getText))

    case (ctx, _, frame: BinaryWebSocketFrame) =>
      streamOut(ctx, WebSocketFrame.Binary(ChannelBufferBuf.Shared(frame.getBinaryData)))
  }


  private val send: PartialFunction[(ChannelHandlerContext, MessageEvent, Object), Unit] = {
    case (ctx, e, _: HttpRequest) =>
      ctx.sendDownstream(e)

    case (ctx, e, _: HttpResponse) =>
      ctx.sendDownstream(e)

    case (ctx, e, _: TextWebSocketFrame) =>
      ctx.sendDownstream(e)

    case (ctx, e, _: BinaryWebSocketFrame) =>
      ctx.sendDownstream(e)

    case (ctx, e, _: PingWebSocketFrame) =>
      ctx.sendDownstream(e)

    case (ctx, e, _: PongWebSocketFrame) =>
      ctx.sendDownstream(e)

    case (ctx, e, _: CloseWebSocketFrame) =>
      ctx.sendDownstream(e)
  }
}

class WebSocketServerHandler extends WebSocketHandler {
  @volatile private[this] var handshaker: Option[WebSocketServerHandshaker] = None

  protected def handleClose(ctx: ChannelHandlerContext, frame: CloseWebSocketFrame): Unit = {
    handshaker foreach { _.close(ctx.getChannel, frame) }
    Channels.close(ctx.getChannel)
  }

  protected def requestWrite: PartialFunction[(ChannelHandlerContext, MessageEvent, Object), Unit] = {
    case (_, _, u) if u.isInstanceOf[Unit] =>
  }

  protected def receiveMsg: PartialFunction[(ChannelHandlerContext, MessageEvent, Object), Unit] = {
    case (ctx, _, req: HttpRequest) =>
      val scheme = if(req.getUri.startsWith("wss")) "wss" else "ws"
      val location = scheme + "://" + req.headers.get(HttpHeaders.Names.HOST) + "/"

      val wsFactory = new WebSocketServerHandshakerFactory(location, null, false)

      Option(wsFactory.newHandshaker(req)) match {
        case None =>
          wsFactory.sendUnsupportedWebSocketVersionResponse(ctx.getChannel)

        case Some(h) =>
          h.handshake(ctx.getChannel, req).toFuture foreach { _ =>
            val webSocket = new WebSocket(
              new URI(req.getUri),
              req.headers.asScala.map { e => e.getKey -> e.getValue }.toMap,
              ctx.getChannel.getRemoteAddress,
              h.getVersion,
              closed
            ) {
              def close(deadline: Time) = Channels.close(ctx.getChannel).toFuture

              def write(msg: WebSocketFrame): Future[Unit] = writeMsg(ctx, msg)

              val stream = AsyncStream.fromReader(readerWriter).asInstanceOf[AsyncStream[WebSocketFrame]]
            }

            Channels.fireMessageReceived(ctx, webSocket)
          }
      }
  }
}

class WebSocketClientHandler extends WebSocketHandler {
  @volatile private[this] var handshaker: Option[WebSocketClientHandshaker] = None

  protected def handleClose(ctx: ChannelHandlerContext, frame: CloseWebSocketFrame): Unit =
    Channels.close(ctx.getChannel)

  protected def requestWrite: PartialFunction[(ChannelHandlerContext, MessageEvent, Object), Unit] = {
    case (ctx, e, start: WebSocketStart) =>
      val wsFactory = new WebSocketClientHandshakerFactory
      val hs = wsFactory.newHandshaker(start.uri, start.version, null, false, start.headers.asJava)

      handshaker = Some(hs)

      hs.handshake(ctx.getChannel).toFuture onFailure {
        e.getFuture.setFailure(_)
      } onSuccess { _ =>
        val webSocket = new WebSocket(
          start.uri,
          start.headers,
          ctx.getChannel.getRemoteAddress,
          start.version,
          closed
        ) {
          def close(deadline: Time) = Channels.close(ctx.getChannel).toFuture

          def write(msg: WebSocketFrame): Future[Unit] = writeMsg(ctx, msg)

          val stream = AsyncStream.fromReader(readerWriter).asInstanceOf[AsyncStream[WebSocketFrame]]
        }

        Channels.fireMessageReceived(ctx, webSocket)

        e.getFuture.setSuccess()
      }
  }

  protected def receiveMsg: PartialFunction[(ChannelHandlerContext, MessageEvent, Object), Unit] = {
    case (ctx, _, res: HttpResponse) if handshaker.isDefined =>
      val hs = handshaker.get
      if (!hs.isHandshakeComplete)
        hs.finishHandshake(ctx.getChannel, res)
  }
}
