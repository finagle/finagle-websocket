import sbt._
import Keys._

object FinagleWebsocket extends Build {
  val libVersion = "6.25.0"

  val baseSettings = Defaults.defaultSettings ++ Seq(
    libraryDependencies ++= Seq(
      "com.twitter" %% "finagle-core" % libVersion,
      "org.scalatest" %% "scalatest" % "2.2.2" % "test",
      "junit" % "junit" % "4.12" % "test"
    )
  )

  lazy val buildSettings = Seq(
    organization := "com.github.finagle",
    version := libVersion,
    scalaVersion := "2.10.4",
    crossScalaVersions := Seq("2.10.4", "2.11.4"),
    scalacOptions ++= Seq("-deprecation", "-feature")
  )

  lazy val publishSettings = Seq(
    publishMavenStyle := true,
    publishArtifact := true,
    publishTo := Some(Resolver.file("localDirectory", file(Path.userHome.absolutePath + "/workspace/mvn-repo"))),
    licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    homepage := Some(url("https://github.com/finagle/finagle-websocket")),
    pomExtra := (
      <scm>
        <url>git://github.com/finagle/finagle-websocket.git</url>
        <connection>scm:git://github.com/finagle/finagle-websocket.git</connection>
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

  lazy val finagleWebsocketRoot = Project(
    id = "finagle-websocket-root",
    base = file("."),
    settings = Project.defaultSettings ++ buildSettings
  ).aggregate(finagleWebsocket)

  lazy val finagleWebsocket =
    finProject("websocket")
}

