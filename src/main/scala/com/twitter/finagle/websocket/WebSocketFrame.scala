package com.twitter.finagle.websocket

import com.twitter.io.Buf

sealed trait WebSocketFrame extends Buf {
  val underlying: Buf

  def write(output: Array[Byte], off: Int): Unit =
    underlying.write(output, off)

  def length: Int =
    underlying.length

  def slice(from: Int, until: Int): Buf =
    underlying.slice(from, until)

  def unsafeByteArrayBuf: Option[Buf.ByteArray] = underlying match {
    case buf: Buf.ByteArray => Some(buf)
    case _ => None
  }
}

object WebSocketFrame {
  case class Text(underlying: Buf) extends WebSocketFrame

  object Text {
    def apply(str: String): Text = Text(Buf.Utf8(str))

    def unapply(frame: Buf): Option[String] = frame match {
      case f: Text => val Buf.Utf8(str) = f.underlying; Some(str)
      case _ => None
    }
  }

  case class Binary(underlying: Buf) extends WebSocketFrame

  case class Ping(underlying: Buf = Buf.Empty) extends WebSocketFrame

  case class Pong(underlying: Buf = Buf.Empty) extends WebSocketFrame

  sealed abstract class Close(val code: Int) extends WebSocketFrame {
    val underlying = Buf.Empty
    val reason: Option[String]
  }
  case class NormalClose(reason: Option[String] = None) extends Close(1000)
  case class GoingAway(reason: Option[String] = None) extends Close(1001)
  case class Error(reason: Option[String] = None) extends Close(1002)
  case class Invalid(reason: Option[String] = None) extends Close(1003)
}
