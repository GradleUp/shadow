# Shadow

Gradle plugin for creating fat/uber JARs with support for package relocation.

> [!NOTE]\
> Previously this plugin was developed by [@johnrengelman](https://github.com/johnrengelman) and published under the
> ID [`com.github.johnrengelman.shadow`][johnrengelman's]
> before maintenance was transferred to the [GradleUp organization](https://github.com/GradleUp) to ensure future
> development, see [#908](https://github.com/GradleUp/shadow/issues/908).
>
> If you are still using the old plugin ID in your build script, we recommend to switch to the new plugin ID [
`com.gradleup.shadow`][gradleup's]
> and update to the latest version to receive all the latest bug fixes and improvements.

## Documentation

- [User Guide](https://gradleup.com/shadow/)
- [Change Log](src/docs/changes/README.md)

## Current Status

[![Maven Central](https://img.shields.io/maven-central/v/com.gradleup.shadow/shadow-gradle-plugin)](https://central.sonatype.com/artifact/com.gradleup.shadow/shadow-gradle-plugin)
[![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/com.gradleup.shadow/shadow-gradle-plugin?&server=https://oss.sonatype.org/)](https://oss.sonatype.org/content/repositories/snapshots/com/gradleup/shadow/)
[![Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/com.gradleup.shadow)](https://plugins.gradle.org/plugin/com.gradleup.shadow)
[![CI](https://github.com/GradleUp/shadow/actions/workflows/build.yml/badge.svg?branch=main&event=push)](https://github.com/GradleUp/shadow/actions/workflows/build.yml?query=branch:main+event:push)
[![License](https://img.shields.io/github/license/GradleUp/shadow.svg)](LICENSE)

## Compatibility Matrix

| Shadow Version | Min Gradle Version | Min Java Version | Plugin ID                                            |
|----------------|--------------------|------------------|------------------------------------------------------|
| 5.2.0 - 6.1.0  | 5.x - 6.x          | 7                | [`com.github.johnrengelman.shadow`][johnrengelman's] |
| 6.1.0+         | 6.x                | 8                | [`com.github.johnrengelman.shadow`][johnrengelman's] |
| 7.0.0+         | 7.x                | 8                | [`com.github.johnrengelman.shadow`][johnrengelman's] |
| 8.0.0+         | 8.0                | 8                | [`com.github.johnrengelman.shadow`][johnrengelman's] |
| 8.3.0+         | 8.3                | 8                | [`com.gradleup.shadow`][gradleup's]                  |
| 9.0.0+         | 8.3                | 11               | [`com.gradleup.shadow`][gradleup's]                  |



[johnrengelman's]: https://plugins.gradle.org/plugin/com.github.johnrengelman.shadow
[gradleup's]: https://plugins.gradle.org/plugin/com.gradleup.shadow
