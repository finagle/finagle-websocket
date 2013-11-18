## Warning: This is still Beta.

## finagle-websockets

Websockets support for Finagle

[![Build Status](https://travis-ci.org/sprsquish/finagle-websocket.png)](https://travis-ci.org/sprsquish/finagle-websocket)

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
