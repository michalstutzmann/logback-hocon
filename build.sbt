val ScalaVersion = "2.12.0"
val Slf4jVersion = "1.7.21"
val LogbackVersion = "1.1.7"
val ConfigVersion = "1.3.1"

lazy val root = (project in file(".")).
  enablePlugins(GitBranchPrompt, ReleasePlugin).
  settings(
    name := "Logback Hocon",
    organization := "com.github.mwegrz",
    scalaVersion := ScalaVersion,
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % Slf4jVersion,
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "com.typesafe" % "config" % ConfigVersion
    ),
    // Publishing
    publishMavenStyle := true,
    crossPaths := false,
    autoScalaLibrary := false,
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    pomExtra := (
      <url>http://github.com/mwegrz/logback-hocon</url>
        <licenses>
          <license>
            <name>Apache License 2.0</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:mwegrz/logback-hocon.git</url>
          <connection>scm:git:git@github.com:mwegrz/logback-hocon.git</connection>
        </scm>
        <developers>
          <developer>
            <id>mwegrz</id>
            <name>Michał Węgrzyn</name>
            <url>http://github.com/mwegrz</url>
          </developer>
        </developers>),
    releaseTagComment := s"Released ${(version in ThisBuild).value}",
    releaseCommitMessage := s"Set version to ${(version in ThisBuild).value}"
  )
