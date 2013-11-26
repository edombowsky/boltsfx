import AssemblyKeys._

name := "BoltSFX"

organization := "com.ventyx"

version := "0.1.0"

scalaVersion := "2.10.3"

assemblySettings

libraryDependencies ++= {
    Seq(
        "org.scalafx" %% "scalafx" % "1.0.0-M6",
        "org.scalatest" % "scalatest_2.10" % "2.0" % "test",
        "junit" % "junit" % "4.11" % "test",
        "com.github.retronym.SbtOneJar.oneJarSettings: _*
    )
}

// Add dependency on JavaFX library based on JAVA_HOME variable
unmanagedJars in Compile += Attributed.blank(file(scala.util.Properties.javaHome) / "lib" / "jfxrt.jar")

mainClass in (Compile, run) := Some("com.ventyx.servicesuite.boltsfx.gui.Main")

mainClass in assembly := Some("com.ventyx.servicesuite.boltsfx.gui.Main")

fork in run := true

fork in Test := true

scalacOptions ++= Seq("-unchecked", "-deprecation","-feature")

resolvers ++= Seq("snapshots"     at "http://oss.sonatype.org/content/repositories/snapshots",
                  "releases"      at "http://oss.sonatype.org/content/repositories/releases"
                )