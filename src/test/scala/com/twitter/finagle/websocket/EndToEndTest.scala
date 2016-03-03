package com.twitter.finagle.websocket

import com.twitter.conversions.time._
import com.twitter.finagle.{ HttpWebSocket, Service }
import com.twitter.io.Buf
import com.twitter.util._
import java.net.InetSocketAddress
import javax.net.ssl.SSLContext
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import scala.collection.mutable.ArrayBuffer

@RunWith(classOf[JUnitRunner])
class EndToEndTest extends FunSuite {
  test("multi client") {
    var result = ""
    val binaryResult = ArrayBuffer.empty[Byte]
    val latch = new CountDownLatch(10)

    val srv = HttpWebSocket.serve("127.0.0.1:0", new Service[WebSocket, Unit] {
      def apply(sock: WebSocket): Future[Unit] = {
        sock.stream foreach {
          case WebSocketFrame.Text(str) =>
            synchronized { result += str }
            latch.countDown()

          case WebSocketFrame.Binary(buf) =>
            val res = new Array[Byte](1)
            buf.write(res, 0)

            synchronized { binaryResult ++= res }
            latch.countDown()

          case _ =>
        }
        Future.Done
      }
    })

    val addr = srv.boundAddress.asInstanceOf[InetSocketAddress]
    val target = s"ws://127.0.0.1:${addr.getPort}/"

    val sockets = (0 until 5) map { _ =>
      Await.result(HttpWebSocket.open(target))
    }

    sockets foreach { sock =>
      FuturePool.unboundedPool {
        Await.ready(sock.write("1") before sock.write(Array[Byte](0x02)))
      }
    }

    try latch.within(1.second)
    finally Await.ready(Closable.all(srv +: sockets: _*).close())
    assert(result == "11111")
    assert(binaryResult === ArrayBuffer(0x02, 0x02, 0x02, 0x02, 0x02))
  }

  test("keep alive and ping pong") {
    val ping = new CountDownLatch(1)
    val pong = new CountDownLatch(1)

    val srv = HttpWebSocket.serve("127.0.0.1:0", PingPong andThen new Service[WebSocket, Unit] {
      def apply(sock: WebSocket): Future[Unit] = {
        sock.stream foreach {
          case WebSocketFrame.Ping(_) => ping.countDown()
          case _ =>
        }
        Future.Done
      }
    })

    val addr = srv.boundAddress.asInstanceOf[InetSocketAddress]
    val target = s"ws://127.0.0.1:${addr.getPort}/"
    val clnt = Await.result(HttpWebSocket.open(target, 100.millisecond))
    clnt.stream foreach {
      case WebSocketFrame.Pong(_) => pong.countDown()
      case _ =>
    }

    try {
      ping.within(1.second)
      pong.within(1.second)
    } finally {
      Await.ready(Closable.all(srv, clnt).close())
    }
  }

  Seq(
    WebSocketFrame.NormalClose(),
    WebSocketFrame.GoingAway(),
    WebSocketFrame.Error(),
    WebSocketFrame.Invalid()
  ) foreach { testFrame =>
    test(s"close frame: ${testFrame.getClass.getName}") {
      val latch = new CountDownLatch(1)
      val closed = new CountDownLatch(1)
      val srv = HttpWebSocket.serve("127.0.0.1:0", new Service[WebSocket, Unit] {
        def apply(sock: WebSocket): Future[Unit] = {
          sock.stream foreach { frame => if (frame == testFrame) latch.countDown() }
          sock.closed foreach { _ => closed.countDown() }
          Future.Done
        }
      })

      val addr = srv.boundAddress.asInstanceOf[InetSocketAddress]
      val target = s"ws://127.0.0.1:${addr.getPort}/"
      val clnt = Await.result(HttpWebSocket.open(target))
      clnt.write(testFrame)

      try {
        latch.within(500.milliseconds)
        closed.within(500.milliseconds)
        Await.result(clnt.closed, 500.milliseconds)
      } finally {
        Await.ready(Closable.all(srv, clnt).close())
      }
    }
  }
}
