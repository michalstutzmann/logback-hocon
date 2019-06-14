import ReleaseTransformations._

val ScalaVersion = "2.12.8"
val Slf4jVersion = "1.7.25"
val LogbackVersion = "1.2.3"
val ConfigVersion = "1.3.4"

lazy val root = (project in file(".")).
  enablePlugins(ReleasePlugin).
  settings(
    name := "Logback Hocon",
    organization := "com.github.mwegrz",
    scalaVersion := ScalaVersion,
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % Slf4jVersion,
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "com.typesafe" % "config" % ConfigVersion
    ),
    // Release settings
    releaseTagName := { (version in ThisBuild).value },
    releaseTagComment := s"Release version ${(version in ThisBuild).value}",
    releaseCommitMessage := s"Set version to ${(version in ThisBuild).value}",
    releaseCrossBuild := true, // true if you cross-build the project for multiple Scala versions
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommandAndRemaining("publishSigned"),
      setNextVersion,
      commitNextVersion,
      releaseStepCommandAndRemaining("sonatypeReleaseAll"),
      pushChanges
    ),
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    // Publish settings
    crossPaths := false,
    autoScalaLibrary := false,
    publishTo := Some(
      if (isSnapshot.value)
        Opts.resolver.sonatypeSnapshots
      else
        Opts.resolver.sonatypeStaging
    ),
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    licenses := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.html")),
    homepage := Some(url("http://github.com/mwegrz/logback-hocon")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/mwegrz/logback-hocon.git"),
        "scm:git@github.com:mwegrz/logback-hocon.git"
      )
    ),
    developers := List(
      Developer(
        id = "mwegrz",
        name = "Michał Węgrzyn",
        email = null,
        url = url("http://github.com/mwegrz")
      )
    )
  )
