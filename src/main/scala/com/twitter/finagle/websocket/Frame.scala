package com.twitter.finagle.websocket

import com.twitter.io.Buf

/**
 * Represents various WebSocket frames.
 *
 * This is a simplification of the frame types described in RFC6455[1]. Notably
 * absent are Continuation and Close. Close is handled directly in the
 * pipeline, initiating the close handshake. Continuations are treated as
 * Binary frames, which means we lose the ability to determine fragmentation.
 *
 * [1]: https://tools.ietf.org/html/rfc6455
 */
sealed trait Frame

object Frame {
  case class Text(text: String) extends Frame
  case class Binary(buf: Buf) extends Frame
  case class Ping(buf: Buf) extends Frame
  case class Pong(buf: Buf) extends Frame
}
