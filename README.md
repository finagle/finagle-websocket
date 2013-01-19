## finagle-websockets

Websockets support for Finagle

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
    <version>0.0.1</version>
    <scope>compile</scope>
  </dependency>

sbt

  resolvers += "com.github.sprsquish" at "https://raw.github.com/sprsquish/mvn-repo/master"

  "com.github.sprsquish" % "finagle-websockets" % "0.0.1"

### Client

  import com.twitter.finagle.builder.ClientBuilder
  import com.twitter.finagle.websocket.{WebSocket, WebSocketCodec}
  import com.twitter.concurrent.Broker

  val clientFactory = ClientBuilder()
    .codec(new WebSocketCodec)
    .hosts(address)
    .hostConnectionLimit(1)
    .buildFactory()

  val outgoing = new Broker[String]

  clientFactory() foreach { client =>
    val socket = new WebSocket {
      val messages = outgoing.recv
      def release() = client.release()
    }

    client(socket) foreach { resp =>
      resp.messages foreach { incoming => ... }
    }
  }

### Server

  import com.twitter.concurrent.Broker
  import com.twitter.finagle.Service
  import com.twitter.finagle.builder.ServerBuilder
  import com.twitter.finagle.websocket.{WebSocket, WebSocketCodec}
  import com.twitter.util.Future
  import java.net.InetSocketAddress

  val service = new Service[WebSocket, WebSocket] {
    def apply(req: WebSocket): Future[WebSocket] = {
      val outgoing = new Broker[String]
      val socket = new WebSocket {
        val messages = outgoing.recv
        def release() = ()
      }
      req.messages foreach { incoming => ... }
      Future.value(socket)
    }
  }

  val server = ServerBuilder()
    .name("WebSocketService")
    .bindTo(new InetSocketAddress(8080))
    .codec(new WebSocketCodec)
    .build(service)
