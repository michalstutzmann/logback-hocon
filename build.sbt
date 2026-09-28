val Slf4jVersion = "1.7.36"
val LogbackVersion = "1.2.13"
val ConfigVersion = "1.4.9"
val JunitInterfaceVersion = "0.13.3"

lazy val root = (project in file("."))
  .settings(
    name := "logback-hocon",
    organization := "com.github.mwegrz",
    versionScheme := Some("early-semver"),
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % Slf4jVersion,
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "com.typesafe" % "config" % ConfigVersion,
      "com.github.sbt" % "junit-interface" % JunitInterfaceVersion % Test
    ),
    javacOptions ++= Seq("--release", "8"),
    // Java-only library: no Scala suffix, no scala-library dependency
    crossPaths := false,
    autoScalaLibrary := false,
    // Publish settings
    publishTo := localStaging.value,
    credentials ++= (for {
      username <- sys.env.get("SONATYPE_CENTRAL_USERNAME")
      password <- sys.env.get("SONATYPE_CENTRAL_PASSWORD")
    } yield Credentials("Sonatype Nexus Repository Manager", "central.sonatype.com", username, password)).toSeq,
    publishMavenStyle := true,
    Test / publishArtifact := false,
    pomIncludeRepository := { _ => false },
    licenses := Seq("Apache License, Version 2.0" -> uri("https://www.apache.org/licenses/LICENSE-2.0.html")),
    homepage := Some(uri("https://github.com/michalstutzmann/logback-hocon")),
    scmInfo := Some(
      ScmInfo(
        uri("https://github.com/michalstutzmann/logback-hocon.git"),
        "scm:git:git@github.com:michalstutzmann/logback-hocon.git"
      )
    ),
    developers := List(
      Developer(
        id = "michalstutzmann",
        name = "Michal Stutzmann",
        email = null,
        url = uri("https://github.com/michalstutzmann")
      )
    )
  )
