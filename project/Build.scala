import sbt._
import Keys._

object FinagleWebsockets extends Build {
  val finagleVersion = "6.8.1"

  val baseSettings = Defaults.defaultSettings ++ Seq(
    libraryDependencies ++= Seq(
      "com.twitter" %% "finagle-core" % finagleVersion,
      "org.scalatest" %% "scalatest" % "1.9.1" % "test",
      "junit" % "junit" % "4.8.1" % "test"
    ))

  lazy val buildSettings = Seq(
    organization := "com.github.sprsquish",
    version := finagleVersion,
    crossScalaVersions := Seq("2.9.2", "2.10.0")
  )

  lazy val publishSettings = Seq(
    publishMavenStyle := true,
    publishArtifact := true,
    publishTo := Some(Resolver.file("localDirectory", file(Path.userHome.absolutePath + "/workspace/mvn-repo"))),
    licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    homepage := Some(url("https://github.com/sprsquish/finagle-websockets")),
    pomExtra := (
      <scm>
        <url>git://github.com/sprsquish/finagle-websockets.git</url>
        <connection>scm:git://github.com/sprsquish/finagle-websockets.git</connection>
      </scm>
        <developers>
          <developer>
            <id>sprsquish</id>
            <name>Jeff Smick</name>
            <url>https://github.com/sprsquish</url>
          </developer>
        </developers>)
  )

  lazy val root = Project(id = "finagle-websockets",
    base = file("."),
    settings = Defaults.itSettings ++ baseSettings ++ buildSettings ++ publishSettings)
      .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
      .configs( IntegrationTest)

}
