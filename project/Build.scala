import sbt._
import Keys._

object FinagleWebsocket extends Build {
  val libVersion = "17.12.0"

  val baseSettings = Defaults.coreDefaultSettings ++ Seq(
    libraryDependencies ++= Seq(
      "com.twitter" %% "finagle-core" % libVersion,
      "com.twitter" %% "finagle-netty3" % libVersion,
      "org.scalatest" %% "scalatest" % "3.0.1" % Test,
      "junit" % "junit" % "4.12" % Test
    )
  )

  lazy val buildSettings = Seq(
    organization := "com.github.finagle",
    version := libVersion,
    scalaVersion := "2.12.2",
    crossScalaVersions := Seq("2.11.11", "2.12.2"),
    scalacOptions ++= Seq("-deprecation", "-feature", "-Xexperimental")
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

  lazy val finagleWebsocket = Project(
    id = "finagle-websocket",
    base = file("."),
    settings =
      Defaults.itSettings ++
      baseSettings ++
      buildSettings ++
      publishSettings
  ).configs(IntegrationTest)
}
