package com.twitter.finagle.irc.server

import com.twitter.finagle.Irc
import com.twitter.server.TwitterServer
import com.twitter.util.Await
import java.net.InetSocketAddress

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
