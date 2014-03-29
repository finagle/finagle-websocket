package com.twitter.finagle.irc.server

import com.twitter.finagle.irc._
import com.twitter.finagle.irc.protocol._
import com.twitter.concurrent.Broker
import com.twitter.util.Future
import scala.collection.mutable

case class ChannelUser(session: Session) {
  var op: Boolean = false
  var voice: Boolean = false

  def chanModeString = {
    var out = ""
    if (op) out += "@"
    if (voice) out += "+"
    out
  }

  def modeString =
    session.modeString + chanModeString

  def ! = session ! _

  def part = session.part(_)
  def join = session.join(_)

  def nick = session.nick
  def name = session.name
  def realName = session.realName
  def hostName = session.hostName

  def isVisible = !session.invisible

  def display =
    chanModeString + nick
}

case class Channel(name: String) {
  private[this] var _topic: Option[String] = None
  private[this] val _users = mutable.Set.empty[ChannelUser]
  private[this] val _modes = mutable.Set.empty[String]

  def topic = _topic

  def findUser(session: Session): Option[ChannelUser] =
    _users find { _.session == session }

  def users: Set[Session] =
    _users map { _.session } toSet

  def modes: Set[String] =
    _modes toSet

  def visibleUsers =
    _users filter { _.isVisible }

  def !(msg: Message): Future[Unit] =
    Future.join(_users map { _ ! msg } toSeq)

  def setTopic(session: Session, topic: Option[String]): Future[Unit] = {
    // TODO: check permissions
    _topic = topic
    this ! session.from(Topic(name, _topic))
  }

  def who(session: Session): Future[Unit] = Future.join(
    _users map { user =>
      // TODO: hopcount, servername
      session ! RplWhoReply(name, user.name, user.hostName, "", user.nick, user.modeString, 0, user.realName)
    } toSeq
  ) flatMap { _ => session ! RplEndOfWho(name) }

  def msg(session: Session, text: String): Future[Unit] = {
    //TODO: check for permission
    Future.join(
      _users map { u =>
        if (u.session == session) Future.Done
        else u ! session.from(PrivMsg(Seq(name), text))
      } toSeq
    )
  }

  // TODO
  def op(session: Session): Future[Unit] =
    join(session)

  def join(session: Session): Future[Unit] = {
    if (_users find { _.session == session } isDefined)
      return Future.Done

    // TODO: permissions check
    // TODO: op on new room?
    val user = new ChannelUser(session)
    _users += user
    user.join(this)

    for {
      _ <- this ! session.from(Join(Seq(name)))

      _ <- user ! RplChannelModeIs(name, modes)
      _ <- user ! (_topic match {
             case Some(t) => RplTopic(name, t)
             case None => RplNoTopic(name)
           })
      _ <- user ! RplNameReply(name, _users map { _.display } toSet)
      _ <- user ! RplEndOfNames(name)
    } yield ()
  }

  def part(session: Session): Future[Unit] = findUser(session) match {
    case Some(user) =>
      this ! session.from(Part(Seq(name), Some(user.nick))) onSuccess { _ =>
        _users -= user
        user.part(this)
      }

    case None =>
      Future.Done
  }

  def quit(session: Session, msg: Option[String]): Future[Unit] = findUser(session) match {
    case Some(user) =>
      _users -= user
      this ! session.from(Quit(msg))

    case None =>
      Future.Done
  }
}
