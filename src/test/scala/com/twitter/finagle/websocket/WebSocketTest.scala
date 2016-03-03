package com.twitter.finagle.websocket

import com.twitter.io.Buf
import java.util.Arrays
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class WebSocketTest extends FunSuite {
  test("WebSocketFrame") {
    val txt = WebSocketFrame.Text("foo")
    val WebSocketFrame.Text(str) = txt
    assert(str === txt)

    val buf = Buf.ByteArray(0x01: Byte)
    val binFrame = WebSocketFrame.Binary(buf)
    val WebSocketFrame.Binary(out) = binFrame
    assert(out === buf)

    val buf2 = Buf.ByteArray(0x01, 0x02, 0x03, 0x04)
    val bin = WebSocketFrame.Binary(buf2)
    assert(bin.length === buf2.length)
    assert(bin.slice(0, 2) === buf2.slice(0, 2))

    val binArr = new Array[Byte](4)
    bin.write(binArr, 0)
    val buf2Arr = new Array[Byte](4)
    buf2.write(buf2Arr, 0)

    assert(Arrays.equals(binArr, buf2Arr))
  }
}
