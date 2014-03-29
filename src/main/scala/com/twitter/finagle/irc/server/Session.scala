package com.twitter.finagle.irc.server

import com.twitter.finagle.irc._
import com.twitter.finagle.irc.protocol._
import com.twitter.concurrent.Broker
import com.twitter.util.Future
import scala.collection.mutable

class Session(server: Server, handle: IrcHandle) {
  private[this] var welcomed = false
  private[this] def welcome() {
    if (!welcomed &&
        _nick != "" &&
        _name != "" &&
        _realName != ""
    ) {
      welcomed = true
      server.welcome(this)
    }
  }

  private[this] val chans = mutable.Map.empty[String, Channel]
  private[this] val modes = mutable.Set.empty[String]
  private[this] val out = new Broker[Message]

  private[this] var _nick = ""
  private[this] var _name = ""
  private[this] var _realName = ""
  private[this] var _hostName = server.hostname

  private[this] var op: Boolean = false
  private[this] var away: Boolean = false

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

  def setNick(nick: String) {
    _nick = nick
    welcome()
  }

  def updateInfo(name: String, realName: String) {
    _name = name
    _realName = realName
    welcome()
  }

  def quit(msg: Option[String]): Future[Unit] =
    Future.join(chans map { case (_, c) => c.quit(this, msg) } toSeq) map { _ => handle.close() }

  def isKnown = welcomed
  def isUnKnown = !isKnown
  def isInvisible = modes contains "i"

  def nick = _nick
  def name = _name
  def realName = _realName
  def hostName = _hostName

  def modeString = {
    var out = if (away) "H" else "G"
    if (op) out += "*"
    out
  }

  def visible = !invisible
  def invisible = modes contains "i"

  def from(msg: Message) =
    ServerMessage(_nick, Some(_name), Some(_hostName), msg)

  override def hashCode = _nick.hashCode
  def equals = _nick.equals(_)
}
