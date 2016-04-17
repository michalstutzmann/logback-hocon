lazy val root = (project in file(".")).
  enablePlugins(GitBranchPrompt, ReleasePlugin).
  settings(
    name := "Logback Hocon",
    organization := "com.github.mwegrz",
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % "1.7.18",
      "ch.qos.logback" % "logback-classic" % "1.1.6",
      "com.typesafe" % "config" % "1.3.0"
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
            <name>MIT</name>
            <url>https://opensource.org/licenses/MIT</url>
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
