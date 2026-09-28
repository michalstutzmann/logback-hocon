# Agent Instructions

## Project Overview

Java library that configures Logback from the `logback` section of a HOCON
config. See [README.md](README.md) for features, installation and the
configuration format.

Key files:

- `src/main/java/com/github/mwegrz/logbackhocon/HoconConfigurator.java`: the
  configurator, registered through
  `src/main/resources/META-INF/services/ch.qos.logback.classic.spi.Configurator`.
- `src/main/resources/reference.conf`: defaults, also used to validate user
  config.
- `build.sbt`: build and Maven Central publishing settings.
- `publish`: CI script run by `.github/workflows/publish.yml`.

## Development Guidelines

- The library is Java-only: keep `crossPaths := false` and
  `autoScalaLibrary := false`, and do not add Scala cross-building.
- Keep `javacOptions` at `--release 8` so the jar runs on Java 8+, whatever
  JDK builds it.
- Stay on Logback 1.2.x and SLF4J 1.7.x. Logback 1.3+ changes the
  `Configurator` API and requires code changes.
- When adding or changing a config key, appender or policy, update
  `HoconConfigurator`, `reference.conf` (for defaults), the README tables and
  the tests together.

## Validation

- Run `sbt testFull`, not `sbt test`: sbt 2 caches test results and `test`
  can skip tests that passed before, even after `clean`.
- sbt 2 runs a background server, so `set ...` commands persist across
  invocations. Run `sbt shutdown` after experimenting with `set`.
- For changes to `publish`, run `bash -n publish` and `shellcheck publish`.

## Releasing

- The version comes from `git-semver-release`, not from the build; there is no
  `version.sbt`.
- Pushing a `v`-prefixed tag (e.g. `v0.1.8`) publishes to Maven Central and
  creates a GitHub release. Central releases cannot be deleted or overwritten,
  so never push tags or run `publish` without explicit approval.
- Branch pushes only run the tests.
