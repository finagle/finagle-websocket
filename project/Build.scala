import sbt._
import Keys._

object FinagleIrc extends Build {
  val libVersion = "6.13.1"

  val baseSettings = Defaults.defaultSettings ++ Seq(
    libraryDependencies ++= Seq(
      "com.twitter" %% "finagle-core" % libVersion,
      "com.twitter" %% "twitter-server" % "1.6.1"
    ),
    resolvers += "twitter-repo" at "http://maven.twttr.com"
  )

  lazy val buildSettings = Seq(
    organization := "com.github.sprsquish",
    version := libVersion,
    crossScalaVersions := Seq("2.9.2", "2.10.0")
  )

  lazy val publishSettings = Seq(
    publishMavenStyle := true,
    publishArtifact := true,
    publishTo := Some(Resolver.file("localDirectory", file(Path.userHome.absolutePath + "/workspace/mvn-repo"))),
    licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    homepage := Some(url("https://github.com/sprsquish/finagle-irc")),
    pomExtra := (
      <scm>
        <url>git://github.com/sprsquish/finagle-irc.git</url>
        <connection>scm:git://github.com/sprsquish/finagle-irc.git</connection>
      </scm>
        <developers>
          <developer>
            <id>sprsquish</id>
            <name>Jeff Smick</name>
            <url>https://github.com/sprsquish</url>
          </developer>
        </developers>)
  )

  lazy val root = Project(id = "finagle-irc",
    base = file("."),
    settings = Defaults.itSettings ++ baseSettings ++ buildSettings ++ publishSettings)
      .configs(IntegrationTest)

}
