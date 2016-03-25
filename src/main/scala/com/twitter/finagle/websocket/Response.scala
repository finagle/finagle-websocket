package com.twitter.finagle.websocket

import com.twitter.concurrent.AsyncStream

case class Response(messages: AsyncStream[Frame])

