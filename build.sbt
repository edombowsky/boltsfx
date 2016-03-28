organization := "com.abb"

name := "BoltSfx"

version := "0.1-SNAPSHOT"

scalaVersion in ThisBuild := "2.11.7"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

// Be sure to remove the libraries you dont need
libraryDependencies ++= Seq(
  "org.scalafx"                %% "scalafx"             % "8.0.60-R9",
  "org.scalafx"                %% "scalafxml-core-sfx8" % "0.2.2",
  "com.typesafe.scala-logging" %% "scala-logging"       % "3.1.0",
  "ch.qos.logback"             %  "logback-classic"     % "1.1.3"
)

resolvers += Resolver.sonatypeRepo("releases")

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

// How to add modena.css into the classpath
// https://groups.google.com/forum/#!topic/scalafx-users/MzHb19SISHQ
unmanagedJars in Compile += {
  val ps = new sys.SystemProperties
  val jh = ps("java.home")
  Attributed.blank(file(jh) / "lib/ext/jfxrt.jar")
}
