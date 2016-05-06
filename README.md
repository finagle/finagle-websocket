# Finagle Websocket

[![Build status](https://travis-ci.org/finagle/finagle-websocket.svg?branch=master)](https://travis-ci.org/finagle/finagle-websocket)
[![Coverage status](https://img.shields.io/coveralls/finagle/finagle-websocket/master.svg)](https://coveralls.io/r/finagle/finagle-websocket?branch=master)
[![Project status](https://img.shields.io/badge/status-inactive-yellow.svg)](#status)

Websockets support for Finagle

## Status

This project is inactive. While we are keep it up to date with new Finagle
releases, it is not currently being actively used or developed. If you're using
Finagle Websocket and would be interested in discussing co-ownership, please
[file an issue](https://github.com/finagle/finagle-websocket/issues).

## Using websockets

### Adding dependencies

Maven

    <repositories>
      <repository>
        <id>com.github.sprsquish</id>
        <url>https://raw.github.com/sprsquish/mvn-repo/master/</url>
        <layout>default</layout>
      </repository>
    </repositories>

    <dependency>
      <groupId>com.github.sprsquish</groupId>
      <artifactId>finagle-websockets_2.9.2</artifactId>
      <version>6.8.1</version>
      <scope>compile</scope>
    </dependency>

sbt

    resolvers += "com.github.sprsquish" at "https://raw.github.com/sprsquish/mvn-repo/master"

    "com.github.sprsquish" %% "finagle-websockets" % "6.8.1"

## Example

The following client and server can be run by pasting the code into `sbt
console`. The client sends "1" over to the server. The server responds by
translating the numeral into an word (mostly). When the client receives this
word, it will send back a number which represents the length of characters of
the received word.

### Client

```scala
import com.twitter.concurrent.AsyncStream
import com.twitter.conversions.time._
import com.twitter.finagle.Websocket
import com.twitter.finagle.util.DefaultTimer
import com.twitter.finagle.websocket.{Frame, Request}
import com.twitter.util.Promise
import java.net.URI

implicit val timer = DefaultTimer.twitter

// Responds to messages from the server.
def handler(messages: AsyncStream[Frame]): AsyncStream[Frame] =
  messages.flatMap {
    case Frame.Text(message) =>
      // Print the received message.
      println(message)

      AsyncStream.fromFuture(
        // Sleep for a second...
        Future.sleep(1.second).map { _ =>
          // ... and then send a message to the server.
          Frame.Text(message.length.toString)
        })

    case _ => AsyncStream.of(Frame.Text("??"))
  }

val incoming = new Promise[AsyncStream[Frame]]
val outgoing =
  Frame.Text("1") +:: handler(
    AsyncStream.fromFuture(incoming).flatten)

val client = Websocket.client.newService(":14000")
val req = Request(new URI("/"), Map.empty, null, outgoing)

// Take the messages of the response and fulfill `incoming`.
client(req).map(_.messages).proxyTo(incoming)
```

### Server

```scala
import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.{Service, Websocket}
import com.twitter.finagle.websocket.{Frame, Request, Response}
import com.twitter.util.Future

// A server that when given a number, responds with a word (mostly).
def handler(messages: AsyncStream[Frame]): AsyncStream[Frame] = {
  messages.map {
    case Frame.Text("1") => Frame.Text("one")
    case Frame.Text("2") => Frame.Text("two")
    case Frame.Text("3") => Frame.Text("three")
    case Frame.Text("4") => Frame.Text("cuatro")
    case Frame.Text("5") => Frame.Text("five")
    case Frame.Text("6") => Frame.Text("6")
    case _ => Frame.Text("??")
  }
}

Websocket.serve(":14000", new Service[Request, Response] {
  def apply(req: Request): Future[Response] =
    Future.value(Response(handler(req.messages)))
})
```
