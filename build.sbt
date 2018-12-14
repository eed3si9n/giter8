import Dependencies._
import CrossVersion.partialVersion

val g8version = "0.12.0-SNAPSHOT"

val javaVmArgs: List[String] = {
  import scala.collection.JavaConverters._
  java.lang.management.ManagementFactory.getRuntimeMXBean.getInputArguments.asScala.toList
}

ThisBuild / organization := "org.foundweekends.giter8"
ThisBuild / version := g8version
ThisBuild / scalaVersion := scala212
ThisBuild / organizationName := "foundweekends"
ThisBuild / organizationHomepage := Some(url("http://foundweekends.org/"))
ThisBuild / scalacOptions ++= Seq("-language:_", "-deprecation", "-Xlint", "-Xfuture")
ThisBuild / publishArtifact in (Compile, packageBin) := true
ThisBuild / homepage := Some(url("http://www.foundweekends.org/giter8/"))
ThisBuild / publishMavenStyle := true
ThisBuild / publishTo :=
  Some(
    "releases" at
      "https://oss.sonatype.org/service/local/staging/deploy/maven2")
ThisBuild / publishArtifact in Test := false
ThisBuild / parallelExecution in Test := false
ThisBuild / licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / developers := List(
  Developer("n8han", "Nathan Hamblen", "@n8han", url("http://github.com/n8han")),
  Developer("eed3si9n", "Eugene Yokota", "@eed3si9n", url("https://github.com/eed3si9n"))
)
ThisBuild / scmInfo := Some(
  ScmInfo(url("https://github.com/foundweekends/giter8"), "git@github.com:foundweekends/giter8.git"))

lazy val root = (project in file("."))
  .enablePlugins(TravisSitePlugin, NoPublish)
  .aggregate(app, lib, scaffold, plugin)
  .settings(
    name := "giter8 root",
    crossScalaVersions := List(scala212, scala211, scala210),
    siteGithubRepo := "foundweekends/giter8",
    siteEmail := { "eed3si9n" + "@" + "gmail.com" },
    skip in publish := true,
    customCommands,
    crossScalaVersions := List(),
  )

lazy val app = (project in file("app"))
  .disablePlugins(BintrayPlugin)
  .enablePlugins(ConscriptPlugin, BuildInfoPlugin, SonatypePublish)
  .dependsOn(lib)
  .settings(
    description := "Command line tool to apply templates defined on github",
    name := "giter8",
    crossScalaVersions := List(scala210, scala211, scala212),
    sourceDirectory in csRun := {
      (baseDirectory).value.getParentFile / "src" / "main" / "conscript"
    },
    libraryDependencies ++= Seq(scopt, logback),
    buildInfoKeys := Seq(name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "giter8"
  )

lazy val crossSbt = Seq(
  crossSbtVersions := List(sbt013, sbt1),
  scalaVersion := {
    val crossSbtVersion = (sbtVersion in pluginCrossBuild).value
    partialVersion(crossSbtVersion) match {
      case Some((0, 13)) => scala210
      case Some((1, _))  => scala212
      case _ =>
        throw new Exception(s"unexpected sbt version: $crossSbtVersion (supported: 0.13.x or 1.X)")
    }
  }
)

lazy val scaffold = (project in file("scaffold"))
  .enablePlugins(SbtPlugin, BintrayPublish, ScriptedPlugin)
  .dependsOn(lib)
  .settings(crossSbt)
  .settings(
    name := "sbt-giter8-scaffold",
    description := "sbt plugin for scaffolding giter8 templates",
    crossScalaVersions := List(scala212, scala210),
    scalaVersion := scala212,
    pluginCrossBuild / sbtVersion := {
      partialVersion(scalaVersion.value) match {
        case Some((2, 10)) => sbt013
        case _             => sbt1
      }
    },
    scriptedLaunchOpts ++= javaVmArgs.filter(
      a => Seq("-Xmx", "-Xms", "-XX").exists(a.startsWith)
    ),
    scriptedBufferLog := false,
    scriptedLaunchOpts += ("-Dplugin.version=" + version.value),
    scripted := scripted
      .dependsOn(publishLocal in lib)
      .evaluated,
    test in Test := {
      scripted.toTask("").value
    }
  )

lazy val plugin = (project in file("plugin"))
  .enablePlugins(SbtPlugin, BintrayPublish, ScriptedPlugin)
  .dependsOn(lib)
  .settings(crossSbt)
  .settings(
    name := "sbt-giter8",
    description := "sbt plugin for testing giter8 templates",
    crossScalaVersions := List(scala212, scala210),
    scalaVersion := scala212,
    pluginCrossBuild / sbtVersion := {
      partialVersion(scalaVersion.value) match {
        case Some((2, 10)) => sbt013
        case _             => sbt1
      }
    },
    resolvers += Resolver.typesafeIvyRepo("releases"),
    scriptedLaunchOpts ++= javaVmArgs.filter(
      a => Seq("-Xmx", "-Xms", "-XX").exists(a.startsWith)
    ),
    scriptedBufferLog := false,
    scriptedLaunchOpts += ("-Dplugin.version=" + version.value),
    scripted := scripted
      .dependsOn(publishLocal in lib)
      .evaluated,
    libraryDependencies += {
      val crossSbtVersion = (sbtVersion in pluginCrossBuild).value

      val artifact =
        partialVersion(crossSbtVersion) match {
          case Some((0, 13)) =>
            "org.scala-sbt" % "scripted-plugin"
          case Some((1, _)) =>
            "org.scala-sbt" %% "scripted-plugin"
          case _ =>
            throw new Exception(s"unexpected sbt version: $crossSbtVersion (supported: 0.13.x or 1.X)")
        }

      artifact % crossSbtVersion
    },
    test in Test := {
      scripted.toTask("").value
    }
  )

lazy val lib = (project in file("library"))
  .disablePlugins(BintrayPlugin)
  .enablePlugins(SonatypePublish)
  .settings(crossSbt)
  .settings(
    name := "giter8-lib",
    description := "shared library for app and plugin",
    crossScalaVersions := List(scala210, scala211, scala212),
    libraryDependencies ++= Seq(
      stringTemplate,
      jgit,
      commonsIo,
      plexusArchiver,
      scalacheck % Test,
      sbtIo % Test,
      scalatest % Test,
      scalamock % Test,
      "org.slf4j" % "slf4j-simple" % "1.7.25" % Test
    ) ++
      (CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, scalaMajor)) if scalaMajor >= 11 =>
          Seq(scalaXml, parserCombinator)
        case _ => Nil
      })
  )

def customCommands: Seq[Setting[_]] = Seq(
  commands += Command.command("release") { state =>
    "clean" ::
      s"++${scala210}" ::
      "lib/publishSigned" ::
      "app/publishSigned" ::
      "plugin/publishSigned" ::
      "scaffold/publishSigned" ::
      s"++${scala211}" ::
      "app/publishSigned" ::
      "lib/publishSigned" ::
      s"++${scala212}" ::
      "lib/publishSigned" ::
      "app/publishSigned" ::
      "plugin/publishSigned" ::
      "scaffold/publishSigned" ::
      "reload" ::
      state
  }
)
