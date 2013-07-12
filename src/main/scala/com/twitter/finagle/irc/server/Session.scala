package com.twitter.finagle.irc.server

import com.twitter.finagle.irc._
import com.twitter.finagle.irc.protocol._
import com.twitter.concurrent.Broker
import com.twitter.util.Future
import scala.collection.mutable

case class SessionUser(
  var session: Session,
  var nick: String = "",
  var name: String = "",
  var realName: String = "",
  var hostName: String = ""
) {
  def !(msg: Message): Future[Unit] =
    session ! msg

  val key: Option[String] = None
  val op: Boolean = false
  val away: Boolean = false

  var chans: mutable.Set[Channel] = mutable.Set.empty[Channel]
  var modes: mutable.Set[String] = mutable.Set.empty[String]

  def visible = !invisible
  def invisible = modes contains "i"

  def modeString = {
    var out = if (away) "H" else "G"
    if (op) out += "*"
    out
  }

  override def hashCode = nick.hashCode
  def equals = nick.equals(_)

  def join(chan: Channel) = session.join(chan)
  def part(chan: Channel) = session.part(chan)
}

class Session(server: Server, handle: IrcHandle) {
  private[this] var welcomed = false
  private[this] def welcome() {
    if (!welcomed &&
        user.nick != "" &&
        user.name != "" &&
        user.realName != ""
    ) {
      welcomed = true
      server.welcome(this)
    }
  }

  private[this] val chans = mutable.Map.empty[String, Channel]
  private[this] val modes = mutable.Set.empty[String]
  private[this] val out = new Broker[Message]
  private[this] var _nick = ""

  val user = SessionUser(this, hostName = server.hostname)

  def join(chan: Channel) {
    synchronized { chans(chan.name) = chan }
  }

  def part(chan: Channel) {
    synchronized { chans.remove(chan.name) }
  }

  def recv =
    out.recv

  def !(msg: Message): Future[Unit] =
    out ! msg

  def serverMsg(msg: Message) =
    ServerMessage(user.nick, Some(user.name), Some(user.hostName), msg)

  def setNick(nick: String) {
    _nick = nick
    user.nick = nick
    welcome()
  }

  def updateInfo(name: String, realName: String) {
    user.name = name
    user.realName = realName
    welcome()
  }

  def quit(msg: Option[String]): Future[Unit] =
    Future.collect(chans map { case (_, c) => c.quit(this, msg) } toSeq) map { _ => handle.close() }

  def isKnown = welcomed
  def isUnKnown = !isKnown
  def isInvisible = modes contains "i"

  def nick = _nick
}
