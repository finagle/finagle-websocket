package com.twitter.finagle.websocket

import com.twitter.conversions.time._
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.junit.runner.RunWith
import com.twitter.concurrent.Broker
import com.twitter.finagle.{HttpWebSocket, Service}
import com.twitter.util._
import scala.collection.mutable.ArrayBuffer

@RunWith(classOf[JUnitRunner])
class EndToEndTest extends FunSuite {
  test("multi client") {
    var result = ""
    val binaryResult = ArrayBuffer.empty[Byte]
    val addr = RandomSocket()
    val latch = new CountDownLatch(10)

    HttpWebSocket.serve(addr, new Service[WebSocket, WebSocket] {
      def apply(req: WebSocket): Future[WebSocket] = {
        val outgoing = new Broker[String]
        val binaryOutgoing = new Broker[Array[Byte]]
        val socket = req.copy(messages = outgoing.recv, binaryMessages = binaryOutgoing.recv)
        req.messages foreach {
          msg =>
            synchronized {
              result += msg
            }
            latch.countDown()
        }
        req.binaryMessages foreach {
          binary =>
            synchronized {
              binaryResult ++= binary
            }
            latch.countDown()
        }
        Future.value(socket)
      }
    })

    val target = "ws://%s:%d/".format(addr.getHostName, addr.getPort)

    val brokers = (0 until 5) map {
      _ =>
        val out = new Broker[String]
        val binaryOut = new Broker[Array[Byte]]
        Await.ready(HttpWebSocket.open(out.recv, binaryOut.recv, target))
        (out, binaryOut)
    }

    brokers foreach {
      pair =>
        FuturePool.unboundedPool {
          pair._1 !! "1"
        }
        FuturePool.unboundedPool {
          pair._2 !! Array[Byte](0x01)
        }
    }

    latch.within(1.second)
    assert(result === "11111")
    assert(binaryResult === ArrayBuffer(0x01, 0x01, 0x01, 0x01, 0x01))
  }
}
