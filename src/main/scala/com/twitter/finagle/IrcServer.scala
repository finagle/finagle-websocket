package com.twitter.finagle.irc

import com.twitter.finagle.irc.protocol._
import com.twitter.server.TwitterServer
import com.twitter.finagle.Irc
import com.twitter.concurrent.{Broker, Offer}
import com.twitter.util.{Await, Future}
import com.twitter.finagle.Service
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.util.Time
import java.net.InetSocketAddress
import scala.collection.mutable

case class RemoteServer(
  name: String)

case class SessionUser(
  session: Session,
  nick: String,
  name: String,
  realName: String,
  modes: Set[String] = Set.empty[String],
  key: Option[String] = None,
  op: Boolean = false,
  away: Boolean = false
) {
  def visible = !invisible
  def invisible = modes contains "i"

  def modeString = {
    var out = if (away) "H" else "G"
    if (op) out += "*"
    out
  }
}

case class Channel(
  name: String,
  topic: Option[String] = None,
  modes: Set[String] = Set.empty[String],
  users: mutable.Set[ChannelUser] = mutable.Set.empty[ChannelUser]
) {
  def visible = users filter { _.user.visible }
}

case class ChannelUser(
  user: SessionUser,
  op: Boolean = false,
  voice: Boolean = false
) {
  def chanModeString = {
    var out = ""
    if (op) out += "@"
    if (voice) out += "+"
    out
  }

  def modeString = user.modeString + chanModeString
}

class Session(server: Server, handle: IrcHandle) {
  val out = new Broker[Message]

  var nick: String = ""
  var user: SessionUser = null
  var welcomed: Boolean = false

  handle.messages foreach {
    case Nick(n, _) =>
      nick = n

    case User(name, _, _, realName) =>
      if (user == null) {
        user = SessionUser(this, nick, name, realName, Set.empty[String])
        server.welcome(this)
      } else {
        user = user.copy(nick = nick, name = name, realName = realName)
      }

    case UnknownCmd(cmd, args) =>
      out ! ErrUknownCommand(cmd)

    case msg => server.handle(msg, this)
  }

  val recv = out.recv map {
    case rw: ResponseWrapper => rw
    case r: Response => ResponseWrapper(server.name, nick, r)
    case m: Message => m
  }

  def isKnown = user != null
  def isUnKnown = !isKnown
  def isInvisible = isKnown && user.invisible
}

class Server(
  val name: String,
  val version: String,
  val hostname: String,
  motd: Seq[String],
  stats: StatsReceiver
) extends Service[IrcHandle, Offer[Message]] {
  val start = Time.now

  var sessions = Set.empty[Session]
  var operators = Set.empty[Session]
  val channels = mutable.Map.empty[String, Channel]

  var servers = Set.empty[RemoteServer]

  def users = sessions filter { _.isKnown }
  def invisibles = sessions filter { _.isInvisible }
  def unknowns = sessions filter { _.isUnKnown }

  def apply(handle: IrcHandle): Future[Offer[Message]] = {
    val session = new Session(this, handle)
    synchronized { sessions += session }
    Future.value(session.recv)
  }

  def welcome(session: Session) {
    session.out ! RplWelcome(session.user.nick, session.user.name, name)
    session.out ! RplYourHost(name, version)
    session.out ! RplCreated(start.toString)
    session.out ! RplMyInfo(name, version, Seq("iOM"), Seq("ntmikbohv"))
    session.out ! RplMotdStart(name)
    motd foreach { msg => session.out ! RplMotd(msg) }
    session.out ! RplEndOfMotd()

    session.out ! RplLUserClient(sessions.size, invisibles.size, servers.size)
    session.out ! RplLUserOp(operators.size)
    session.out ! RplLUserUnknown(unknowns.size)
    session.out ! RplLUserChannels(channels.size)
    session.out ! RplLUserMe(sessions.size, servers.size)
  }

  def handle(msg: Message, session: Session) = msg match {
    case ChanList(Seq("STOP"), _) =>
    case ChanList(chans, _) =>
        session.out ! RplListStart()
        channels foreach { case (_, c) => session.out ! RplList(c.name, c.visible.size, c.topic.getOrElse("")) }
        session.out ! RplListEnd()

    case Join(chans, keys) =>
      chans foreach { name =>
        val user = session.user
        var chanUser = ChannelUser(user)
        val chan = channels getOrElseUpdate(name, {
          chanUser = chanUser.copy(op = true)
          Channel(name)
        })
        chan.users += chanUser
        chan.users foreach {
          _.user.session.out ! ServerMessage(user.nick, Some(user.name), Some(hostname), Join(Seq(chan.name)))
        }
        session.out ! RplChannelModeIs(chan.name, chan.modes)
        session.out ! (chan.topic match {
          case Some(t) => RplTopic(chan.name, t)
          case None => RplNoTopic(chan.name)
        })
        session.out ! RplNameReply(chan.name, chan.users.map { u => u.chanModeString + u.user.nick } toSet)
        session.out ! RplEndOfNames(chan.name)
      }

    case Part(chans, _) =>
      for { n <- chans; chan <- channels.get(n) } {
        chan.users find(_.user == session.user) foreach { chanUser =>
          val user = chanUser.user
          chan.users foreach { _.user.session.out ! ServerMessage(user.nick, Some(user.name), Some(hostname), Part(Seq(chan.name), Some(user.nick))) }
          chan.users -= chanUser
        }
      }

    case Who(Some(chan), _) =>
      channels.get(chan) foreach { chan =>
        chan.users foreach { u =>
          val user = u.user
          // TODO: hopcount
          session.out ! RplWhoReply(chan.name, user.name, hostname, name, user.nick, u.modeString, 0, user.realName)
        }
        session.out ! RplEndOfWho(chan.name)
      }

    case ChanBanMaskMode(None, chan, _) =>
      session.out ! RplEndOfBanList(chan)

    case PrivMsg(chans, text) =>
      val user = session.user
      for { c <- chans; chan <- channels.get(c); u <- chan.users if u.user != user } {
        u.user.session.out ! ServerMessage(user.nick, Some(user.name), Some(hostname), PrivMsg(Seq(chan.name), text))
      }

    case Ping(s) =>
      session.out ! ServerMessage(name, None, None, Pong(s))

    case msg =>
      session.out ! ErrUknownCommand(msg.encode)
  }
}

object Main extends TwitterServer {
  val port = flag("irc.port", new InetSocketAddress(6060), "Port the irc server will listen on")
  val serverName = flag("irc.name", "finagle-irc-server", "Name of the server")
  val version = flag("irc.version", "1.0", "Server version number")
  val motdFile = flag("irc.motd", "", "MOTD file")

  def main() {
    val motd = motdFile.get match {
      case Some(f) => scala.io.Source.fromFile(f, "utf-8").getLines.toSeq
      case None => Seq.empty[String]
    }
    val server = Irc.serve(port(), new Server(serverName(), version(), "localhost", motd, statsReceiver))
    onExit { server.close() }
    Await.ready(server)
  }
}
