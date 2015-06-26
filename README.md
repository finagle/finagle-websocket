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

### Client

    import com.twitter.finagle.HttpWebSocket
    import com.twitter.concurrent.Broker

    val out = new Broker[String]
    HttpWebSocket.open(out.recv, "ws://localhost:8080/") onSuccess { resp =>
      resp.messages foreach println
    }

### Server

    import com.twitter.concurrent.Broker
    import com.twitter.finagle.{HttpWebSocket, Service}
    import com.twitter.finagle.websocket.WebSocket
    import com.twitter.util.Future
    import java.net.InetSocketAddress

    val server = HttpWebSocket.serve(":8080", new Service[WebSocket, WebSocket] {
      def apply(req: WebSocket): Future[WebSocket] = {
        val outgoing = new Broker[String]
        val socket = req.copy(messages = outgoing.recv)
        req.messages foreach { outgoing ! _.reverse }
        Future.value(socket)
      }
    })
