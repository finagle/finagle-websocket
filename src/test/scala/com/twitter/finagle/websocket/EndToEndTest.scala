package com.twitter.finagle.websocket

import com.twitter.concurrent.Broker
import com.twitter.conversions.time._
import com.twitter.finagle
import com.twitter.finagle.{HttpWebSocket, Service}
import com.twitter.io.Buf
import com.twitter.util._
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import scala.collection.mutable.ArrayBuffer

@RunWith(classOf[JUnitRunner])
class EndToEndTest extends FunSuite {
  import Frame._

  test("multi client") {
    var result = ""
    val binaryResult = ArrayBuffer.empty[Byte]
    val addr = RandomSocket()
    val latch = new CountDownLatch(10)

    finagle.Websocket.serve(addr, new Service[Request, Response] {
      def apply(req: Request): Future[Response] = {
        req.messages.foreach {
          case Text(msg) =>
            synchronized { result += msg }
            latch.countDown()
          case Binary(buf) =>
            val binary = Buf.ByteArray.Owned.extract(buf)
            synchronized { binaryResult ++= binary }
            latch.countDown()
          case _ =>
        }
        Future.value(Response(req.messages))
      }
    })

    val target = "ws://%s:%d/".format(addr.getHostName, addr.getPort)

    val brokerPairs = (0 until 5) map { _ =>
      val textOut = new Broker[String]
      val binaryOut = new Broker[Array[Byte]]
      Await.ready(HttpWebSocket.open(textOut.recv, binaryOut.recv, target))
      (textOut, binaryOut)
    }

    brokerPairs foreach { pair =>
      val (textBroker, binaryBrocker) = pair
      FuturePool.unboundedPool { textBroker !! "1" }
      FuturePool.unboundedPool { binaryBrocker !! Array[Byte](0x01) }
    }

    latch.within(1.second)
    assert(result === "11111")
    assert(binaryResult === ArrayBuffer(0x01, 0x01, 0x01, 0x01, 0x01))
  }
}
