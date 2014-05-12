import sbt._
import Keys._

object FinagleLibs extends Build {
  val libVersion = "6.15.0"

  val baseSettings = Defaults.defaultSettings ++ Seq(
    libraryDependencies ++= Seq(
      "com.twitter" %% "finagle-core" % libVersion,
      "org.scalatest" %% "scalatest" % "1.9.1" % "test",
      "junit" % "junit" % "4.8.1" % "test"
    )
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
    homepage := Some(url("https://github.com/sprsquish/finagle-libs")),
    pomExtra := (
      <scm>
        <url>git://github.com/sprsquish/finagle-libs.git</url>
        <connection>scm:git://github.com/sprsquish/finagle-libs.git</connection>
      </scm>
        <developers>
          <developer>
            <id>sprsquish</id>
            <name>Jeff Smick</name>
            <url>https://github.com/sprsquish</url>
          </developer>
        </developers>)
  )

  def finProject(n: String): Project = {
    val name = "finagle-" + n
    Project(
      id = name,
      base = file(name),
      settings =
        Defaults.itSettings ++
        baseSettings ++
        buildSettings ++
        publishSettings
    ).configs(IntegrationTest)
  }

  lazy val finagleLibs = Project(
    id = "finagle-libs",
    base = file("."),
    settings = Project.defaultSettings
  ).aggregate(finagleIrc, finagleWebsocket)

  lazy val finagleIrc =
    finProject("irc")

  lazy val finagleIrcServer =
    finProject("irc-server").settings(
      libraryDependencies ++= Seq(
        "com.twitter" %% "twitter-server" % "1.6.1")
    ).dependsOn(finagleIrc)

  lazy val finagleWebsocket =
    finProject("websocket")
}
