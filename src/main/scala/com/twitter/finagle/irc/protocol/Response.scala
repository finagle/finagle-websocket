package com.twitter.finagle.irc.protocol

sealed abstract class Response(val code: Int) extends Message

case class ResponseWrapper(server: String, nick: String, resp: Response) extends Response(resp.code) {
  def encode = ":%s %03d %s %s".format(server, code, nick, resp.encode)
}

case class ErrNoSuchServer(server: String) extends Response(402) {
  def encode = "%s :No such server".format(server)
}

case class ErrUknownCommand(cmd: String) extends Response(421) {
  def encode = "%s :Unkown command".format(cmd)
}

case class RplWelcome(nick: String, user: String, server: String) extends Response(1) {
  def encode = ":Welcome to the Internet Relay Network %s!%s@%s".format(nick, user, server)
}

case class RplYourHost(server: String, ver: String) extends Response(2) {
  def encode = ":Your host is %s, running version %s".format(server, ver)
}

case class RplCreated(date: String) extends Response(3) {
  def encode = ":This server was created %s".format(date)
}

case class RplMyInfo(server: String, ver: String, uModes: Seq[String], cModes: Seq[String]) extends Response(4) {
  def encode = "%s %s +%s +%s".format(server, ver, uModes.mkString, cModes.mkString)
}

case class RplNone() extends Response(300) {
  def encode = ""
}

case class RplListStart() extends Response(321) {
  def encode = ""
}

case class RplList(chan: String, visible: Int, topic: Option[String]) extends Response(322) {
  def encode = "%s %d :%s".format(chan, visible, topic.getOrElse(""))
}

case class RplListEnd() extends Response(323) {
  def encode = ":End of /LIST"
}

case class RplMotdStart(server: String) extends Response(375) {
  def encode = ":- %s Message of the day - ".format(server)
}

case class RplMotd(msg: String) extends Response(372) {
  def encode = ":- %s".format(msg)
}

case class RplEndOfMotd() extends Response(376) {
  def encode = "End of /MOTD command"
}

case class RplLUserClient(users: Int, invisible: Int, servers: Int) extends Response(251) {
  def encode = ":There are %s users and %s invisible on %s servers".format(users, invisible, servers)
}

case class RplLUserOp(ops: Int) extends Response(252) {
  def encode = "%d :operator(s) online".format(ops)
}

case class RplLUserUnknown(count: Int) extends Response(253) {
  def encode = "%d :unknown connection(s)".format(count)
}

case class RplLUserChannels(count: Int) extends Response(254) {
  def encode = "%d :channels formed".format(count)
}

case class RplLUserMe(clients: Int, servers: Int) extends Response(255) {
  def encode = ":I have %d clients and %d servers".format(clients, servers)
}

case class RplChannelModeIs(chan: String, mode: Set[String]) extends Response(324) {
  // TODO: modes
  def encode = "%s %s %s".format(chan, "", "")
}

case class RplNameReply(chan: String, names: Set[String]) extends Response(353) {
  def encode = "= %s :%s".format(chan, names.mkString(" "))
}

case class RplEndOfNames(chan: String) extends Response(366) {
  def encode = "%s :End of /NAMES list".format(chan)
}

case class RplNoTopic(chan: String) extends Response(331) {
  def encode = "%s :No topic is set".format(chan)
}

case class RplTopic(chan: String, topic: String) extends Response(332) {
  def encode = "%s :%s".format(chan, topic)
}

case class RplWhoReply(chan: String, user: String, host: String, server: String, nick: String, mode: String, hops: Int, realName: String) extends Response(352) {
  def encode = "%s %s %s %s %s %s :%d %s".format(
    chan, user, host, server, nick, mode, hops, realName)
}

case class RplEndOfWho(chan: String) extends Response(315) {
  def encode = "%s :End of /WHO list".format(chan)
}

case class RplEndOfBanList(chan: String) extends Response(368) {
  def encode = "%s :End of channel ban list".format(chan)
}
