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
  nick: String,
  name: String,
  realName: String,
  modes: Set[String] = Set.empty[String],
  key: Option[String] = None
) {
  def visible = !invisible
  def invisible = modes contains "i"
}

case class Channel(
  name: String,
  topic: Option[String] = None,
  modes: Set[String] = Set.empty[String],
  users: mutable.Set[SessionUser] = mutable.Set.empty[SessionUser]
) {
  def visible = users filter { _.visible }
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
        user = SessionUser(nick, name, realName, Set.empty[String])
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

  def welcome(session: Session): Future[Unit] = for {
    _ <- session.out ! RplWelcome(session.user.nick, session.user.name, name)
    _ <- session.out ! RplYourHost(name, version)
    _ <- session.out ! RplCreated(start.toString)
    _ <- session.out ! RplMyInfo(name, version, Seq("iOM"), Seq("ntmikbohv"))
    _ <- session.out ! RplMotdStart(name)
    _ <- Future.collect(motd map { msg => session.out ! RplMotd(msg) })
    _ <- session.out ! RplEndOfMotd()

    _ <- session.out ! RplLUserClient(sessions.size, invisibles.size, servers.size)
    _ <- session.out ! RplLUserOp(operators.size)
    _ <- session.out ! RplLUserUnknown(unknowns.size)
    _ <- session.out ! RplLUserChannels(channels.size)
    _ <- session.out ! RplLUserMe(sessions.size, servers.size)
  } yield ()

  def handle(msg: Message, session: Session) = msg match {
    case ChanList(Seq("STOP"), _) =>
    case ChanList(chans, _) =>
      for {
        _ <- session.out ! RplListStart()
        _ <- Future.collect(channels map { case (_, c) => session.out ! RplList(c.name, c.visible.size, c.topic.getOrElse("")) } toSeq)
        _ <- session.out ! RplListEnd()
      } {}

    case Join(chans, keys) =>
      chans foreach { name =>
        val chan = channels getOrElseUpdate(name, Channel(name))
        val user = session.user
        chan.users += user
        session.out ! ServerMessage(user.nick, user.name, hostname, Join(Seq(chan.name)))
        session.out ! RplChannelModeIs(chan.name, chan.modes)
        session.out ! (chan.topic match {
          case Some(t) => RplTopic(chan.name, t)
          case None => RplNoTopic(chan.name)
        })
        session.out ! RplNameReply(chan.name, chan.users.map(_.nick).toSet)
        session.out ! RplEndOfNames(chan.name)
      }

    case Who(Some(chan), _) =>
      channels.get(chan) foreach { chan =>
        chan.users foreach { user =>
          // TODO: host, <H|G>[*][@|+], hopcount
          session.out ! RplWhoReply(chan.name, user.name, "", name, user.nick, "G", 0, user.realName)
        }
        session.out ! RplEndOfWho(chan.name)
      }

    case ChanBanMaskMode(None, chan, _) =>
      session.out ! RplEndOfBanList(chan)

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
