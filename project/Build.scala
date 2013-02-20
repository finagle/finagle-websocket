import sbt._
import Keys._

object FinagleWebsockets extends Build {

  val baseSettings = Defaults.defaultSettings ++ Seq(
    resolvers += "twitter-repo" at "http://maven.twttr.com",
    libraryDependencies ++= Seq(
      "com.twitter" %% "finagle-core" % "6.1.0"
    ))

  lazy val buildSettings = Seq(
    organization := "com.github.sprsquish",
    version := "6.1.0",
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
      .configs( IntegrationTest)

}
