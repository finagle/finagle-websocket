package com.twitter.finagle.irc

object Protocol {

  private[this] sealed trait Message {
    def encode: String
  }

  private[this] sealed trait Decoder[T <: Message] extends PartialFunction[Seq[String], T]

  def decode(buf: ChannelBuffer): Message = {
    val frameLength = buf.bytesBefore(FindCRLF)
  }

  var decoders = Map.empty[String, Decoder[_]]

  case class Pass(pass: String) extends Message {
    def encode = "PASS %s".format(msg.pass)
  }
  decoders += ("PASS", { case Array(pass) => Pass(pass) })

  case class Nick(name: String, hopCount: Option[Int] = None) extends Message {
    def encode = {
      val out = "NICK %s".format(name)
      hopCount map { "%s %d".format(out, _) } getOrElse out
    }
  }
  decoders += ("NICK", {
    case Array(name, hops) => Nick(name, Some(hops.toInt))
    case Array(name) => Nick(name)
  })

  case class User(name: String, hostName: String, serverName: String, realName: String) extends Message {
    def encode = "USER %s %s %s :%s".format(name, hostName, serverName, realName)
  }
  decoders += ("USER", { case Array(n, hn, sn, rn) => User(n, hn, sn, rn.tail) })

  case class Server(name: String, hopCount: Int, info: String) extends Message {
    def encode = "SERVER %s %d :%s".format(name, hopCount, info)
  }
  decoders += ("SERVER", { case Array(n, hc, i) => Server(n, hc, i.tail) })

  case class Oper(user: String, pass: String) extends Message {
    def encode = "OPER %s %s".format(user, pass)
  }
  decoders += ("OPER", { case Array(u, p) => Oper(u, p) })

  case class Quit(msg: Option[String] = None) extends Message {
    def encode = msg map { "QUIT :%s".format(msg) } getOrElse "QUIT"
  }
  decoders += ("QUIT", {
    case Array(msg) => Quit(Some(msg.tail))
    case Array() => Quit()
  })

  case class ServerQuit(server: String, comment: String) extends Message {
    def encode = "SQUIT %s :%s".format(server, comment)
  }
  decoders += ("SQUIT", { case Array(s, c) => ServerQuit(s, c.tail) })

  case class Join(channels: Seq[String], keys: Seq[String] = Seq.empty[String]) extends Message {
    def encode = "JOIN %s %s".format(channels.mkString(","), keys.mkString(","))
  }
  decoders += ("JOIN", {
    case Array(chans, keys) => Join(chans.split(','), keys.split(','))
    case Array(chans) => Join(chans.split(','))
  })

  case class Part(channels: Seq[String]) extends Message {
    def encode = "PART %s".format(channels.mkString(","))
  }
  decoders += ("PART", { case Array(chans) => Part(chans.split(',')) })

  case class Mode(mode: String, limit: Option[String] = None, user: Option[String] = None, mask: Option[String] = None) extends Message {
    def encode = {
      var tkns = Seq("MODE")
      tkns.mkString(" ")
    }
    val code = "MODE"
  }

  case class Topic(channel: String, topic: Option[String] = None) extends Message {
    val code = "TOPIC"
  }

  case class Names(channels: Seq[String]) extends Message {
    val code = "NAMES"
  }

  case class List(channels: Option[Seq[String]] = None, server: Option[String] = None) extends Message {
    val code = "LIST"
  }

  case class Invite(name: String, channel: String) extends Message {
    val code = "INVITE"
  }

  case class Kick(channel: String, user: String, comment: Option[String] = None) extends Message {
    val code = "KICK"
  }

  case class Version(server: Option[String] = None) extends Message {
    val code = "VERSION"
  }

  case class Stats(query: Option[String] = None, server: Option[String] = None) extends Message {
    val code = "STATS"
  }

  case class Links(server: Option[String] = None, mask: Option[String] = None) extends Message {
    val code = "LINKS"
  }

  case class Time(server: Option[String] = None) extends Message {
    val code = "TIME"
  }

  case class Connect(server: String, port: Option[Int] = None, remoteServer: Option[String] = None) extends Message {
    val code = "CONNECT"
  }

  case class Trace(server: Option[String]) = None extends Message {
    val code = "TRACE"
  }

  case class Admin(server: Option[String] = None) extends Message {
    val code = "ADMIN"
  }

  case class Info(server: Option[String] = None) extends Message {
    val code = "INFO"
  }

  case class PrivateMsg(receivers: Seq[String], text: String) extends Message {
    val code = "PRIVMSG"
  }

  case class Notice(name: String, text: String) extends Message {
    val code = "NOTICE"
  }

  case class Who(name: Option[String] = None, op: Boolean = false) extends Message {
    val code = "WHO"
  }

  case class WhoIs(server: Option[String] = None, nicks: Seq[String]) extends Message {
    val code = "WHOIS"
  }

  case class WhoWas(name: String, count: Option[Int] = None, server: Option[String] = None) extends Message {
    val code = "WHOWAS"
  }

  case class Kill(name: String, comment: String) extends Message {
    val code = "KILL"
  }

  case class Ping(servers: Seq[String]) extends Message {
    val code = "PING"
  }

  case class Pong(daemons: Seq[String]) extends Message {
    val code = "PONG"
  }

  case class Error(msg: String) extends Message {
    val code = "ERROR"
  }

  case class Away(msg: Option[String] = None) extends Message {
    val code = "AWAY"
  }
}
