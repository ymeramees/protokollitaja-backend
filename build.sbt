import com.typesafe.config.ConfigFactory
import com.typesafe.sbt.packager.docker.Cmd

enablePlugins(JavaAppPackaging, DockerPlugin)

Global / onChangedBuildSource := ReloadOnSourceChanges

val conf = ConfigFactory.parseFile(new File("src/main/resources/application.conf")).resolve()

name := "protokollitaja-backend"

version := "0.1.5"

scalaVersion := "2.13.1"

libraryDependencies ++= {
  val akkaVersion = "2.6.+"
  val akkaHttpVersion = "10.1.+"

  Seq(
    "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    //  "com.typesafe.akka" %% "akka-testkit" % "2.6.+" % Test,
    "org.scalatest" %% "scalatest" % "3.1.+" % Test,
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
    "org.json4s" %% "json4s-jackson" % "3.6.+",
//    "org.json4s" %% "json4s-native" % "3.6.+",
    "org.json4s" %% "json4s-mongo" % "3.6.+" exclude("org.mongodb", "mongo-java-driver"),
    "org.apache.kafka" %% "kafka" % "2.4.+",
    "com.github.t3hnar" %% "scala-bcrypt" % "4.1",
    "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
//    "org.mongodb.scala" %% "mongo-scala-bson" % "2.8.0",
//    "com.lightbend.akka" %% "akka-stream-alpakka-mongodb" % "1.1.2"
    "org.mongodb.scala" %% "mongo-scala-driver" % "2.8.0"
  )
}

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)

parallelExecution := false

mainClass in Compile := Some("ee.zone.web.protokollitaja.backend.server.ServerMain")
mainClass in (Compile, run) := Some("ee.zone.web.protokollitaja.backend.server.ServerMain")
mainClass in (Compile, packageBin) := Some("ee.zone.web.protokollitaja.backend.server.ServerMain")

dockerBaseImage := "openjdk:8-jre"
dockerExposedPorts := Seq(conf.getInt("server.port"))
dockerExposedVolumes := Seq("/data")
dockerUpdateLatest := true
//dockerCommands += Cmd("USER", "protokollitaja")
//dockerCommands += Cmd("RUN", "useradd --no-log-init --uid 1001 protokollitaja")
//dockerCommands += Cmd("RUN", "chown 1001:1001 /opt/docker/bin/*")
//dockerCommands += Cmd("RUN", "chown 1001:root /data")
daemonUser in Docker := "protokollitaja"
//dockerCommands += Cmd("USER", (daemonUser in Docker).value)
//dockerCommands := Seq()
//dockerCommands ++= Seq(
    //  Cmd("FROM", "openjdk:8"),
    //  Cmd("LABEL", s"""MAINTAINER="${maintainer.value}""""),
//    Cmd("CONTAINER_NAME", "protokollitaja-backend")
//)
