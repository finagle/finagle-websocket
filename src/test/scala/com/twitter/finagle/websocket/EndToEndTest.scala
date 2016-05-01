package com.twitter.finagle.websocket

import com.twitter.concurrent.AsyncStream
import com.twitter.conversions.time._
import com.twitter.finagle
import com.twitter.finagle.Service
import com.twitter.finagle.param.Stats
import com.twitter.finagle.stats.{NullStatsReceiver, StatsReceiver}
import com.twitter.io.Buf
import com.twitter.util._
import java.net.{InetSocketAddress, SocketAddress, URI}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import scala.collection.mutable.ArrayBuffer

@RunWith(classOf[JUnitRunner])
class EndToEndTest extends FunSuite {
  import Frame._
  import EndToEndTest._
  test("echo") {
    val echo = new Service[Request, Response] {
      def apply(req: Request): Future[Response] =
        Future.value(Response(req.messages))
    }

    connect(echo) { client =>
      val frames = texts("hello", "world")
      for {
        response <- client(mkRequest("/", frames))
        messages <- response.messages.toSeq()
      } yield assert(messages == frames)
    }
  }
}

private object EndToEndTest {
  def connect(
    service: Service[Request, Response],
    stats: StatsReceiver = NullStatsReceiver
  )(run: Service[Request, Response] => Future[Unit]): Unit = {
    val server = finagle.Websocket.server
      .withLabel("server")
      .configured(Stats(stats))
      .serve("localhost:*", service)

    val addr = server.boundAddress.asInstanceOf[InetSocketAddress]

    val client = finagle.Websocket.client
      .configured(Stats(stats))
      .newService(s"${addr.getHostName}:${addr.getPort}", "client")

    Await.result(run(client).ensure(Closable.all(client, server).close()), 1.second)
  }

  def texts(messages: String*): Seq[Frame] =
    messages.map(Frame.Text(_))

  def mkRequest(path: String, frames: Seq[Frame]): Request =
    Request(new URI(path), Map.empty, new SocketAddress{}, AsyncStream.fromSeq(frames))
}
