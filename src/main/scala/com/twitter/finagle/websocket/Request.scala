package com.twitter.finagle.websocket

import com.twitter.concurrent.AsyncStream
import java.net.{SocketAddress, URI}
import scala.collection.immutable

case class Request(
    uri: URI,
    headers: immutable.Map[String, String],
    remoteAddress: SocketAddress,
    messages: AsyncStream[Frame])
