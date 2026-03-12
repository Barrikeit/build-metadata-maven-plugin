# build-metadata-maven-plugin

A lightweight Maven plugin that injects build metadata — build number, timestamp, application name, and version — directly into Maven project properties at the `INITIALIZE` lifecycle phase. Metadata can optionally be written to a `.properties` file on disk.

---

## Table of Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Usage](#usage)
- [Configuration](#configuration)
- [Generated Properties](#generated-properties)
- [File Output](#file-output)
- [Skipping Execution](#skipping-execution)
- [Examples](#examples)
- [Changelog](#changelog)
- [License](#license)

---

## Requirements

| Requirement | Version  |
|-------------|----------|
| Java        | 17+      |
| Maven       | 3.9+     |

---

## Installation

Add the plugin to your project's `pom.xml`:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>dev.barrikeit.maven</groupId>
      <artifactId>build-metadata-maven-plugin</artifactId>
      <version>1.0.0</version>
      <executions>
        <execution>
          <goals>
            <goal>generate-build-metadata</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

---

## Usage

The plugin runs automatically during the `INITIALIZE` phase. No additional configuration is required for basic usage. Once executed, the generated properties are available throughout the rest of the Maven build lifecycle.

To run the goal manually:

```bash
mvn dev.barrikeit.maven:build-metadata-maven-plugin:generate-build-metadata
```

---

## Configuration

All parameters are optional. Defaults are shown below.

```xml
<plugin>
  <groupId>dev.barrikeit.maven</groupId>
  <artifactId>build-metadata-maven-plugin</artifactId>
  <version>1.0.0</version>
  <configuration>
    <!-- Skip plugin execution entirely -->
    <skip>false</skip>

    <!-- Override the application name (defaults to ${project.name}) -->
    <appName/>

    <!-- Override the application version (defaults to ${project.version}) -->
    <appVersion/>

    <!-- Maven property name for the build number -->
    <buildIdProperty>buildNumber</buildIdProperty>

    <!-- Maven property name for the build timestamp -->
    <buildTimestampProperty>buildTimestamp</buildTimestampProperty>

    <!-- Maven property name for the application name -->
    <appNameProperty>appName</appNameProperty>

    <!-- Maven property name for the application version -->
    <appVersionProperty>appVersion</appVersionProperty>

    <!-- Length of the build number hex string (0 = disabled, max = 64) -->
    <buildIdLength>20</buildIdLength>

    <!-- Timestamp format (java.text.SimpleDateFormat pattern) -->
    <buildTimestampFormat>yyyyMMddHHmmssSSS</buildTimestampFormat>

    <!-- Write metadata to a .properties file -->
    <generateFile>false</generateFile>

    <!-- Output directory for the generated file -->
    <fileDirectory>${project.build.directory}/generated-sources/build-metadata</fileDirectory>

    <!-- Name of the generated properties file -->
    <fileName>build.properties</fileName>
  </configuration>
</plugin>
```

### Parameter Reference

| Parameter               | Property                      | Default                                                              | Description                                                                 |
|-------------------------|-------------------------------|----------------------------------------------------------------------|-----------------------------------------------------------------------------|
| `skip`                  | `buildMetadata.skip`          | `false`                                                              | Skip plugin execution entirely.                                             |
| `appName`               | `appName`                     | `${project.name}`                                                    | Override the application name.                                              |
| `appVersion`            | `appVersion`                  | `${project.version}`                                                 | Override the application version.                                           |
| `buildIdProperty`       | `buildIdProperty`             | `buildNumber`                                                        | Maven property key for the generated build number.                          |
| `buildTimestampProperty`| `buildTimestampProperty`      | `buildTimestamp`                                                     | Maven property key for the build timestamp.                                 |
| `appNameProperty`       | `appNameProperty`             | `appName`                                                            | Maven property key for the application name.                                |
| `appVersionProperty`    | `appVersionProperty`          | `appVersion`                                                         | Maven property key for the application version.                             |
| `buildIdLength`         | `buildIdLength`               | `20`                                                                 | Length of the hex build number string. `0` disables it. Max `64`.          |
| `buildTimestampFormat`  | `buildTimestampFormat`        | `yyyyMMddHHmmssSSS`                                                  | `SimpleDateFormat` pattern for the timestamp.                               |
| `generateFile`          | `generateFile`                | `false`                                                              | If `true`, writes metadata to a `.properties` file.                         |
| `fileDirectory`         | `fileDirectory`               | `${project.build.directory}/generated-sources/build-metadata`       | Directory for the output file.                                              |
| `fileName`              | `fileName`                    | `build.properties`                                                   | Name of the output file.                                                    |

---

## Generated Properties

After execution, the following properties are available in the Maven build (using default names):

| Property         | Example Value                       | Description                                      |
|------------------|-------------------------------------|--------------------------------------------------|
| `buildNumber`    | `3f9a1c72e084b56d120a`              | SHA-256-based unique hex identifier (20 chars).  |
| `buildTimestamp` | `20260312143022512`                 | Build date/time in `yyyyMMddHHmmssSSS` format.   |
| `appName`        | `My Application`                    | Project name from POM or override.               |
| `appVersion`     | `1.0.0`                             | Project version from POM or override.            |

These can be referenced anywhere in the build — for example in resource filtering:

```xml
<!-- src/main/resources/application.properties -->
app.name=${appName}
app.version=${appVersion}
build.number=${buildNumber}
build.timestamp=${buildTimestamp}
```

Enable resource filtering in your POM:

```xml
<build>
  <resources>
    <resource>
      <directory>src/main/resources</directory>
      <filtering>true</filtering>
    </resource>
  </resources>
</build>
```

---

## File Output

Set `generateFile` to `true` to write metadata to a `.properties` file:

```xml
<configuration>
  <generateFile>true</generateFile>
  <fileDirectory>${project.build.directory}/generated-sources/build-metadata</fileDirectory>
  <fileName>build.properties</fileName>
</configuration>
```

The generated file will look like:

```properties
#Created by build system. Do not modify.
#Thu Mar 12 14:30:22 CET 2026
buildNumber=3f9a1c72e084b56d120a
buildTimestamp=20260312143022512
appName=My Application
appVersion=1.0.0
```

> **Note:** The `buildNumber` entry is omitted from the file when `buildIdLength` is set to `0`.

---

## Skipping Execution

Skip the plugin for a specific build:

```bash
mvn package -DbuildMetadata.skip=true
```

Or permanently in your POM configuration:

```xml
<configuration>
  <skip>true</skip>
</configuration>
```

---

## Examples

### Minimal — inject properties only

```xml
<plugin>
  <groupId>dev.barrikeit.maven</groupId>
  <artifactId>build-metadata-maven-plugin</artifactId>
  <version>1.0.0</version>
  <executions>
    <execution>
      <goals>
        <goal>generate-build-metadata</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

### Write metadata to a file with a custom timestamp format

```xml
<plugin>
  <groupId>dev.barrikeit.maven</groupId>
  <artifactId>build-metadata-maven-plugin</artifactId>
  <version>1.0.0</version>
  <configuration>
    <buildTimestampFormat>yyyy-MM-dd'T'HH:mm:ss</buildTimestampFormat>
    <generateFile>true</generateFile>
  </configuration>
  <executions>
    <execution>
      <goals>
        <goal>generate-build-metadata</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

### Disable build number generation

```xml
<configuration>
  <buildIdLength>0</buildIdLength>
</configuration>
```

### Use custom property names

```xml
<configuration>
  <buildIdProperty>my.build.id</buildIdProperty>
  <buildTimestampProperty>my.build.time</buildTimestampProperty>
</configuration>
```

---
