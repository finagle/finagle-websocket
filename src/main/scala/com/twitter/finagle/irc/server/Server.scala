package com.twitter.finagle.irc.server

import com.twitter.finagle.irc._
import com.twitter.finagle.irc.protocol._
import com.twitter.concurrent.Offer
import com.twitter.util.Future
import com.twitter.finagle.Service
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.util.Time
import scala.collection.mutable

case class RemoteServer(
  name: String)

class Server(
  val name: String,
  val version: String,
  val hostname: String,
  motd: Seq[String],
  stats: StatsReceiver
) extends Service[IrcHandle, Offer[Message]] {
  val start = Time.now.toString

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
    handle.messages foreach { process(_, session) }
    Future.value(session.recv map {
      case r: Response => ResponseWrapper(name, session.nick, r)
      case msg => msg
    })
  }

  def welcome(session: Session): Future[Unit] = for {
    _ <- session ! RplWelcome(session.nick, session.name, name)
    _ <- session ! RplYourHost(name, version)
    _ <- session ! RplCreated(start)
    _ <- session ! RplMyInfo(name, version, Seq("iOM"), Seq("ntmikbohv"))
    _ <- session ! RplMotdStart(name)
    _ <- Future.join(motd map { msg => session ! RplMotd(msg) })
    _ <- session ! RplEndOfMotd()

    _ <- session ! RplLUserClient(sessions.size, invisibles.size, servers.size)
    _ <- session ! RplLUserOp(operators.size)
    _ <- session ! RplLUserUnknown(unknowns.size)
    _ <- session ! RplLUserChannels(channels.size)
    _ <- session ! RplLUserMe(sessions.size, servers.size)
  } yield ()

  def withChans(chans: Seq[String])(f: Channel => Future[Unit]): Future[Unit] =
    Future.join(chans map { chan =>
      channels.get(chan) match {
        case Some(c) => f(c)
        case None => Future.Done
      }
    })

  def process(msg: Message, session: Session) = msg match {
    case Nick(n, _) =>
      // TODO: dupe checking
      session.setNick(n)

    case User(name, _, _, realName) =>
      session.updateInfo(name, realName)

    case ChanList(Seq("STOP"), _) =>

    case ChanList(chans, _) =>
      for {
        _ <- session ! RplListStart()
        _ <- Future.join(channels map { case (_, c) => session ! RplList(c.name, c.visibleUsers.size, c.topic) } toSeq)
        _ <- session ! RplListEnd()
      } yield ()

    case Join(chans, keys) =>
      Future.join(chans map { name =>
        var newChan = false
        val chan = channels getOrElseUpdate(name, {
          newChan = true
          new Channel(name)
        })

        if (newChan) chan.op(session)
        else chan.join(session)
      })

    case Part(chans, None) =>
      withChans(chans) { _.part(session) }

    case Who(Some(chan), _) =>
      withChans(Seq(chan)) { _.who(session) }

    case ChanBanMaskMode(None, chan, _) =>
      session ! RplEndOfBanList(chan)

    case PrivMsg(chans, text) =>
      withChans(chans) { _.msg(session, text) }

    case Topic(chan, topic) =>
      withChans(Seq(chan)) { _.setTopic(session, topic) }

    case Ping(s) =>
      session ! session.from(Pong(s))

    case Quit(msg) =>
      synchronized { sessions -= session }
      session.quit(msg)

    case UnknownCmd(cmd, _) =>
      session ! ErrUknownCommand(cmd)

    case msg =>
      session ! ErrUknownCommand(msg.encode)
  }
}
