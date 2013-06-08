package com.twitter.finagle.websocket

import com.twitter.conversions.time._
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.junit.runner.RunWith
import com.twitter.concurrent.Broker
import com.twitter.finagle.{HttpWebSocket, Service}
import com.twitter.util._
import java.net.InetSocketAddress

@RunWith(classOf[JUnitRunner])
class EndToEndTest extends FunSuite {
  test("multi client") {
    var result = ""
    val addr = RandomSocket()
    val latch = new CountDownLatch(5)

    val server = HttpWebSocket.serve(addr, new Service[WebSocket, WebSocket] {
      def apply(req: WebSocket): Future[WebSocket] = {
        val outgoing = new Broker[String]
        val socket = req.copy(messages = outgoing.recv)
        req.messages foreach { msg =>
          synchronized { result += msg }
          latch.countDown()
        }
        Future.value(socket)
      }
    })

    val target = "ws://%s:%d/".format(addr.getHostName, addr.getPort)

    val brokers = (0 until 5) map { _ =>
      val out = new Broker[String]
      Await.ready(HttpWebSocket.open(out.recv, target))
      out
    }

    brokers foreach { out =>
      FuturePool.unboundedPool { out !! "1" }
    }

    latch.within(1.second)
    assert(result === "11111")
  }
}
