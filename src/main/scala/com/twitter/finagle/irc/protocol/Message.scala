package com.twitter.finagle.irc.protocol

import org.jboss.netty.buffer.ChannelBuffer

trait Message {
  def encode: String
}

case class ServerMessage(nick: String, name: Option[String], host: Option[String], msg: Message) extends Message {
  def encode = {
    var out = ":%s".format(nick)
    name foreach { n => out = "%s!%s".format(out, n) }
    host foreach { h => out = "%s@%s".format(out, h) }
    "%s %s".format(out, msg.encode)
  }
}

case class UnknownCmd(cmd: String, args: String) extends Message {
  def encode = throw new Exception("Non-Protocol Message")
}

case class Pass(pass: String) extends Message {
  def encode = "PASS %s".format(pass)
}

case class Nick(name: String, hopCount: Option[Int] = None) extends Message {
  def encode = {
    val out = "NICK %s".format(name)
    hopCount map { "%s %d".format(out, _) } getOrElse out
  }
}

case class User(name: String, hostName: String, serverName: String, realName: String) extends Message {
  def encode = "USER %s %s %s :%s".format(name, hostName, serverName, realName)
}

case class Server(name: String, hopCount: Int, info: String) extends Message {
  def encode = "SERVER %s %d :%s".format(name, hopCount, info)
}

case class Oper(user: String, pass: String) extends Message {
  def encode = "OPER %s %s".format(user, pass)
}

case class Quit(msg: Option[String] = None) extends Message {
  def encode = "QUIT :%s".format(msg.getOrElse("Leaving"))
}

case class ServerQuit(server: String, comment: String) extends Message {
  def encode = "SQUIT %s :%s".format(server, comment)
}

case class Join(channels: Seq[String], keys: Seq[String] = Seq.empty[String]) extends Message {
  def encode = "JOIN %s %s".format(channels.mkString(","), keys.mkString(","))
}

case class Part(channels: Seq[String], name: Option[String] = None) extends Message {
  def encode = {
    var out = "PART %s".format(channels.mkString(","))
    name foreach { n => out += " :%s".format(n) }
    out
  }
}

sealed abstract class Mode(modeId: String) extends Message {
  val unset: Option[Boolean]
  val user: String
  def encode = "MODE %s %s%s".format(
    user, unset map { u => if (u) "-" else "+" } getOrElse(""), modeId)
}

sealed abstract class ChanMode(modeId: String, prefix: String = "") extends Mode(modeId) {
  val unset: Option[Boolean]
  val chan: String
  val user = chan
  override def encode = "%s %s".format(super.encode, prefix)
}

case class UserInvisibleMode(unset: Option[Boolean], user: String) extends Mode("i")
case class UserReceiveServerNoticesMode(unset: Option[Boolean], user: String) extends Mode("s")
case class UserReceiveWallopsMode(unset: Option[Boolean], user: String) extends Mode("w")
case class UserOperatorMode(unset: Option[Boolean], user: String) extends Mode("o")

case class ChanOpMode(unset: Option[Boolean], chan: String, nick: String) extends ChanMode("o", nick)
case class ChanPrivateMode(unset: Option[Boolean], chan: String) extends ChanMode("p")
case class ChanSecretMode(unset: Option[Boolean], chan: String) extends ChanMode("s")
case class ChanInviteOnlyMode(unset: Option[Boolean], chan: String) extends ChanMode("i")
case class ChanTopicOpOnlyMode(unset: Option[Boolean], chan: String) extends ChanMode("t")
case class ChanNoOutsideMessagesMode(unset: Option[Boolean], chan: String) extends ChanMode("n")
case class ChanModeratedMode(unset: Option[Boolean], chan: String) extends ChanMode("m")
case class ChanUserLimitMode(unset: Option[Boolean], chan: String, limit: Int) extends ChanMode("l", limit.toString)
case class ChanBanMaskMode(unset: Option[Boolean], chan: String, mask: String) extends ChanMode("b", mask)
case class ChanVoiceMode(unset: Option[Boolean], chan: String, nick: String) extends ChanMode("v", nick)
case class ChanKeyMode(unset: Option[Boolean], chan: String, key: String) extends ChanMode("k", key)

private object Mode {
  private[this] def isChan(chan: String) =
    chan.startsWith("#") || chan.startsWith("&")

  def unapply(tkns: Seq[String]): Option[Message] = {
    val unset = tkns match {
      case _ :: m :: _ if m.startsWith("-") => Some(true)
      case _ :: m :: _ if m.startsWith("+") => Some(false)
      case _ => None
    }

    PartialFunction.condOpt(tkns) {
      case chan :: ("o"|"+o"|"-o") :: user :: Nil if isChan(chan) => ChanOpMode(unset, chan, user)
      case chan :: ("p"|"+p"|"-p") :: Nil if isChan(chan) => ChanPrivateMode(unset, chan)
      case chan :: ("s"|"+s"|"-s") :: Nil if isChan(chan) => ChanSecretMode(unset, chan)
      case chan :: ("i"|"+i"|"-i") :: Nil if isChan(chan) => ChanInviteOnlyMode(unset, chan)
      case chan :: ("t"|"+t"|"-t") :: Nil if isChan(chan) => ChanTopicOpOnlyMode(unset, chan)
      case chan :: ("n"|"+n"|"-n") :: Nil if isChan(chan) => ChanNoOutsideMessagesMode(unset, chan)
      case chan :: ("m"|"+m"|"-m") :: Nil if isChan(chan) => ChanModeratedMode(unset, chan)
      case chan :: ("l"|"+l"|"-l") :: limit :: Nil if isChan(chan) => ChanUserLimitMode(unset, chan, limit.toInt)
      case chan :: ("+b"|"-b") :: mask :: Nil if isChan(chan) => ChanBanMaskMode(unset, chan, mask)
      case chan :: "b" :: Nil if isChan(chan) => ChanBanMaskMode(unset, chan, "")
      case chan :: ("v"|"+v"|"-v") :: user :: Nil if isChan(chan) => ChanVoiceMode(unset, chan, user)
      case chan :: ("k"|"+k"|"-k") :: key :: Nil if isChan(chan) => ChanKeyMode(unset, chan, key)

      case user :: ("i"|"+i"|"-i") :: Nil => UserInvisibleMode(unset, user)
      case user :: ("s"|"+s"|"-s") :: Nil => UserReceiveServerNoticesMode(unset, user)
      case user :: ("w"|"+w"|"-w") :: Nil => UserReceiveWallopsMode(unset, user)
      case user :: ("o"|"+o"|"-o") :: Nil => UserOperatorMode(unset, user)
    }
  }
}

case class Topic(channel: String, topic: Option[String] = None) extends Message {
  def encode = {
    var out = "TOPIC %s".format(channel)
    topic foreach { t => out = "%s :%s".format(out, t) }
    out
  }
}

case class Names(channels: Seq[String] = Seq.empty[String]) extends Message {
  def encode = "NAMES %s".format(channels.mkString(","))
}

case class ChanList(channels: Seq[String] = Seq.empty[String], server: Option[String] = None) extends Message {
  def encode = {
    var out = "LIST %s".format(channels.mkString(","))
    server foreach { s => out = "%s %s".format(out, s) }
    out
  }
}

case class Invite(name: String, channel: String) extends Message {
  def encode = "INVITE %s %s".format(name, channel)
}

case class Kick(channel: String, user: String, comment: Option[String] = None) extends Message {
  def encode = {
    var out = "KICK %s %s".format(channel, user)
    comment foreach { c => out = "%s :%s".format(out, c) }
    out
  }
}

case class Version(server: Option[String] = None) extends Message {
  def encode = {
    var out = "VERSION"
    server foreach { s => out = "%s %s".format(out, s) }
    out
  }
}

case class Stats(query: Option[String] = None, server: Option[String] = None) extends Message {
  def encode = {
    var out = "STATS"
    query foreach { q => out = "%s %s".format(out, q) }
    server foreach { s => out = "%s %s".format(out, s) }
    out
  }
}

case class Admin(server: Option[String] = None) extends Message {
  def encode = {
    var out = "ADMIN"
    server foreach { s => out = "%s %s".format(out, s) }
    out
  }
}

case class Trace(server: Option[String] = None) extends Message {
  def encode = {
    var out = "TRACE"
    server foreach { s => out = "%s %s".format(out, s) }
    out
  }
}

case class Connect(server: String, port: Option[Int] = None, remoteServer: Option[String] = None) extends Message {
  def encode = {
    var out = "CONNECT %s".format(server)
    port foreach { p => out = "%s %d".format(out, p) }
    remoteServer foreach { s => out = "%s %s".format(out, s) }
    out
  }
}

case class ServerTime(server: Option[String] = None) extends Message {
  def encode = {
    var out = "TIME"
    server foreach { t => out = "%s %s".format(out, t) }
    out
  }
}

case class Links(server: Option[String] = None, mask: Option[String] = None) extends Message {
  def encode = {
    var out = "LINKS"
    server foreach { s => out = "%s %s".format(out, s) }
    mask foreach { m => out = "%s %s".format(out, m) }
    out
  }
}

case class Info(server: Option[String] = None) extends Message {
  def encode = {
    var out = "INFO"
    server foreach { s => out = "%s %s".format(out, s) }
    out
  }
}

case class PrivMsg(receivers: Seq[String], text: String) extends Message {
  def encode = "PRIVMSG %s :%s".format(receivers.mkString(","), text)
}

case class Notice(name: String, text: String) extends Message {
  def encode = "NOTICE %s :%s".format(name, text)
}

case class Away(msg: Option[String] = None) extends Message {
  def encode = {
    var out = "AWAY"
    msg foreach { m => out = "%s :%s".format(out, msg) }
    out
  }
}

case class Error(msg: String) extends Message {
  def encode = "ERROR :%s".format(msg)
}

case class Pong(daemons: Seq[String]) extends Message {
  def encode = "PONG %s".format(daemons.mkString(" "))
}

case class Ping(servers: Seq[String]) extends Message {
  def encode = "PING %s".format(servers.mkString(" "))
}

case class Kill(name: String, comment: String) extends Message {
  def encode = "KILL %s %s".format(name, comment)
}

case class WhoWas(name: String, count: Option[Int] = None, server: Option[String] = None) extends Message {
  def encode = {
    var out = "WHOWAS %s".format(name)
    count foreach { c =>
      out = "%s %d".format(out, c)
      server foreach { s => out = "%s %s".format(out, s) }
    }
    out
  }
}

case class WhoIs(server: Option[String] = None, nicks: Seq[String]) extends Message {
  def encode = {
    var out = "WHOIS"
    server foreach { s => out = "%s %s".format(out, s) }
    "%s %s".format(out, nicks.mkString(","))
  }
}

case class Who(name: Option[String] = None, op: Boolean = false) extends Message {
  def encode = {
    var out = "WHO"
    name foreach { n => out = "%s %s %s".format(out, n, if (op) "o" else "") }
    out
  }
}

object Decoders {
  val default = Map[String, PartialFunction[List[String], Message]](
    ("PASS" -> {
      case pass :: Nil => Pass(pass)
    }),

    ("NICK" -> {
      case name :: hops :: Nil => Nick(name, Some(hops.toInt))
      case name :: Nil => Nick(name)
    }),

    ("USER" -> {
      case n :: hn :: sn :: rn :: Nil => User(n, hn, sn, rn)
    }),

    ("SERVER" -> {
      case n :: hc :: i :: Nil => Server(n, hc.toInt, i)
    }),

    ("OPER" -> {
      case u :: p :: Nil => Oper(u, p)
    }),

    ("QUIT" -> {
      case msg :: Nil => Quit(Some(msg))
      case Nil => Quit()
    }),

    ("SQUIT" -> {
      case s :: c :: Nil => ServerQuit(s, c)
    }),

    ("JOIN" -> {
      case chans :: keys :: Nil => Join(chans.split(','), keys.split(','))
      case chans :: Nil => Join(chans.split(','))
    }),

    ("PART" -> {
      case chans :: Nil => Part(chans.split(','))
    }),

    ("MODE" -> {
      case Mode(mode) => mode
    }),

    ("TOPIC" -> {
      case chan :: Nil => Topic(chan)
      case chan :: topic :: Nil => Topic(chan, Some(topic))
    }),

    ("NAMES" -> {
      case Nil => Names()
      case names :: Nil => Names(names.split(","))
    }),

    ("LIST" -> {
      case Nil => ChanList()
      case chans :: Nil => ChanList(chans.split(","))
      case chans :: server :: Nil => ChanList(chans.split(","), Some(server))
    }),

    ("INVITE" -> {
      case name :: chan :: Nil => Invite(name, chan)
    }),

    ("KICK" -> {
      case chan :: user :: Nil => Kick(chan, user)
      case chan :: user :: c :: Nil => Kick(chan, user, Some(c))
    }),

    ("VERSION" -> {
      case Nil => Version()
      case server :: Nil => Version(Some(server))
    }),

    ("STATS" -> {
      case Nil => Stats()
      case query :: Nil => Stats(Some(query))
      case query :: server :: Nil => Stats(Some(query), Some(server))
    }),

    ("LINKS" -> {
      case Nil => Links()
      case mask :: Nil => Links(None, Some(mask))
      case server :: mask :: Nil => Links(Some(server), Some(mask))
    }),

    ("TIME" -> {
      case Nil => ServerTime()
      case server :: Nil => ServerTime(Some(server))
    }),

    ("CONNECT" -> {
      case server :: Nil => Connect(server)
      case server :: port :: Nil => Connect(server, Some(port.toInt))
      case server :: port :: rs :: Nil => Connect(server, Some(port.toInt), Some(rs))
    }),

    ("TRACE" -> {
      case Nil => Trace()
      case server :: Nil => Trace(Some(server))
    }),

    ("ADMIN" -> {
      case Nil => Admin()
      case server :: Nil => Admin(Some(server))
    }),

    ("INFO" -> {
      case Nil => Info()
      case server :: Nil => Info(Some(server))
    }),

    ("PRIVMSG" -> {
      case recvs :: msg :: Nil => PrivMsg(recvs.split(","), msg)
    }),

    ("NOTICE" -> {
      case name :: msg :: Nil => Notice(name, msg)
    }),

    ("WHO" -> {
      case Nil => Who()
      case name :: Nil => Who(Some(name))
      case name :: "o" :: Nil => Who(Some(name), true)
    }),

    ("WHOIS" -> {
      case nicks :: Nil => WhoIs(None, nicks.split(","))
      case server :: nicks :: Nil => WhoIs(Some(server), nicks.split(","))
    }),

    ("WHOWAS" -> {
      case name :: Nil => WhoWas(name)
      case name :: count :: Nil => WhoWas(name, Some(count.toInt))
      case name :: count :: server :: Nil => WhoWas(name, Some(count.toInt), Some(server))
    }),

    ("KILL" -> {
      case name :: comment :: Nil => Kill(name, comment)
    }),

    ("PING" -> {
      case servers => Ping(servers)
    }),

    ("PONG" -> {
      case daemons => Pong(daemons)
    }),

    ("ERROR" -> {
      case msg :: Nil => Error(msg)
    }),

    ("AWAY" -> {
      case Nil => Away()
      case msg :: Nil => Away(Some(msg))
    }))
}

class IrcDecoder(
  decoders: Map[String, PartialFunction[List[String], Message]]
) extends (List[String] => Option[Message]) {
  def apply(tkns: List[String]): Option[Message] = {
    val cmd :: tail = tkns
    decoders.get(cmd.toUpperCase) flatMap { _.lift(tail) }
  }
}

object DefaultIrcDecoder extends IrcDecoder(Decoders.default)
