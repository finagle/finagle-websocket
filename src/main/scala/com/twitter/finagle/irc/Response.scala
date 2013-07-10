package com.twitter.finagle.irc

object Response {
  private var codes = Map.empty[Int, Response]
  def get(code: Int) = codes.get(code) getOrElse {
    throw new Exception("Unknown Error Code")
  }
}

sealed abstract class Response(code: Int) extends Message {
  Response.codes += (code -> this)

  val server: String
  val nick: String
  val info: String

  def encode =
    ":%s %d %s %s".format(server, code, nick, info)
}

case class ErrNoSuchServer(server: String) extends Response(402) {
  val nick = ""
  val info = "%s :No such server".format(server)
}

case class ErrUknownCommand(server: String, nick: String, cmd: String) extends Response(421) {
  val info = "%s :Unkown command".format(cmd)
}

case class RplNone(server: String, nick: String) extends Response(300) {
  val info = ""
}

case class RplListStart(server: String, nick: String) extends Response(321) {
  val info = ""
}

case class RplList(server: String, nick: String, chan: String, visible: Int, topic: String) extends Response(322) {
  val info = "%s %d :%s".format(chan, visible, topic)
}

case class RplListEnd(server: String, nick: String) extends Response(323) {
  val info = ":End of LIST"
}
