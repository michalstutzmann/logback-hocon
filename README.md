# logback-hocon

[![Maven Central](https://img.shields.io/maven-central/v/com.github.mwegrz/logback-hocon)](https://central.sonatype.com/artifact/com.github.mwegrz/logback-hocon)

Configure [Logback](https://logback.qos.ch/) with [HOCON](https://github.com/lightbend/config) instead of XML.

The configuration lives under the `logback` key of your `application.conf`, so logging can be configured alongside the
rest of the application and overridden with the usual Typesafe Config mechanisms (includes, substitutions, system
properties, environment variables).

Requires Java 8 or later and Logback 1.2.x.

## Installation

sbt:

```scala
libraryDependencies += "com.github.mwegrz" % "logback-hocon" % "VERSION"
```

Maven:

```xml
<dependency>
  <groupId>com.github.mwegrz</groupId>
  <artifactId>logback-hocon</artifactId>
  <version>VERSION</version>
</dependency>
```

## Usage

`HoconConfigurator` is registered as a Logback `Configurator` through `ServiceLoader`. When there is no
`logback-test.xml` or `logback.xml` on the classpath, Logback picks it up automatically and configures itself from
`ConfigFactory.load()`.

To (re)configure Logback from a specific `Config` at runtime:

```java
LogbackHocon.configure(config);
```

This resets the current `LoggerContext` and applies the `logback` section of `config`.

## Configuration

```hocon
logback {
  context-name = my-app

  appenders {
    console {
      class = ch.qos.logback.core.ConsoleAppender
      encoder.pattern = "%d %-5level %logger{36} - %msg%n"
    }

    file {
      class = ch.qos.logback.core.rolling.RollingFileAppender
      file = "log/app.log"
      encoder.pattern = "%d %-5level %logger{36} - %msg%n"
      rolling-policy {
        class = ch.qos.logback.core.rolling.TimeBasedRollingPolicy
        file-name-pattern = "log/app.%d{yyyy-MM-dd}.log"
        max-history = 30
      }
    }

    async {
      class = ch.qos.logback.classic.AsyncAppender
      appenders = [file]
    }
  }

  loggers {
    "com.example" {
      level = DEBUG
      appenders = []
    }
  }

  root {
    level = INFO
    appenders = [console, async]
  }
}
```

Loggers inherit their ancestors' appenders, as in Logback. The defaults (`root.level = WARN`, no appenders) are defined
in [`reference.conf`](src/main/resources/reference.conf).

### Appenders

Each entry under `appenders` defines a named appender. Appenders may reference each other regardless of the order in
which they are defined; referencing an undefined appender is an error.

| `class`                                            | Settings                                                                                     |
|----------------------------------------------------|----------------------------------------------------------------------------------------------|
| `ch.qos.logback.core.ConsoleAppender`              | `encoder`, `with-jansi` (default `false`)                                                    |
| `ch.qos.logback.core.FileAppender`                 | `encoder`, `file`, `append` (default `true`)                                                 |
| `ch.qos.logback.core.rolling.RollingFileAppender`  | `encoder`, `file` (optional), `append` (default `true`), `rolling-policy`, `triggering-policy` |
| `ch.qos.logback.classic.AsyncAppender`             | `appenders`, `queue-size`, `discarding-threshold`, `include-caller-data`, `never-block`       |

### Encoders

Either a pattern:

```hocon
encoder.pattern = "%d %-5level %logger{36} - %msg%n"
```

or a `LayoutWrappingEncoder` subclass with an optional layout:

```hocon
encoder {
  class = ch.qos.logback.core.encoder.LayoutWrappingEncoder
  layout.class = com.example.MyLayout
}
```

### Rolling policies

| `rolling-policy.class`                                        | Settings                                                                                        |
|---------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `ch.qos.logback.core.rolling.TimeBasedRollingPolicy`          | `file-name-pattern`, `max-history`, `total-size-cap`, `clean-history-on-start`                  |
| `ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy`   | as above, plus `max-file-size`                                                                  |
| `ch.qos.logback.core.rolling.FixedWindowRollingPolicy`        | `file-name-pattern`, `min-index`, `max-index`; requires a `triggering-policy`                   |

The only supported `triggering-policy.class` is `ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy`, with
`max-file-size`:

```hocon
rolling-policy {
  class = ch.qos.logback.core.rolling.FixedWindowRollingPolicy
  file-name-pattern = "log/app.%i.log"
  max-index = 5
}
triggering-policy {
  class = ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy
  max-file-size = 10MB
}
```

### Other settings

| Key                | Default | Description                                                                                              |
|--------------------|---------|----------------------------------------------------------------------------------------------------------|
| `context-name`     |         | Name of the `LoggerContext`                                                                              |
| `debug`            | `false` | Print Logback's internal status messages to the console (like `<configuration debug="true">`)            |
| `status-listener`  |         | Class name of a `StatusListener` to install, e.g. `ch.qos.logback.core.status.OnConsoleStatusListener`  |
| `jmx-configurator` | `off`   | Register Logback's `JMXConfigurator` MBean (like `<jmxConfigurator/>`)                                   |

### Conversion rules

Custom conversion words for patterns:

```hocon
logback.conversion-rules {
  myword.converter-class = com.example.MyConverter
}
```

## License

[Apache License, Version 2.0](LICENSE)
