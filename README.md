# Gradle Shadow

Gradle plugin for creating fat/uber JARs with support for package relocation.

> [!NOTE]\
> Previously this plugin was developed by [@johnrengelman](https://github.com/johnrengelman) and published under the ID [`com.github.johnrengelman.shadow`](https://plugins.gradle.org/plugin/com.github.johnrengelman.shadow)
> before maintenance was transferred to the [GradleUp organization](https://github.com/GradleUp) to ensure future development, see [#908](https://github.com/GradleUp/shadow/issues/908).
>
> If you are still using the old plugin ID in your build script, we recommend to switch to the new plugin ID [`com.gradleup.shadow`](https://plugins.gradle.org/plugin/com.gradleup.shadow)
> and update to the latest version to receive all the latest bug fixes and improvements.

## Documentation

- [User Guide](https://gradleup.com/shadow/)
- [CHANGELOG](src/docs/changes/README.md)

## Current Status

[![Maven Central](https://img.shields.io/maven-central/v/com.gradleup.shadow/shadow-gradle-plugin)](https://central.sonatype.com/artifact/com.gradleup.shadow/shadow-gradle-plugin)
[![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/com.gradleup.shadow/shadow-gradle-plugin?&server=https://oss.sonatype.org/)](https://oss.sonatype.org/content/repositories/snapshots/com/gradleup/shadow/)
[![Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/com.gradleup.shadow)](https://plugins.gradle.org/plugin/com.gradleup.shadow)
[![CI](https://github.com/GradleUp/shadow/actions/workflows/ci.yml/badge.svg?branch=main&event=push)](https://github.com/GradleUp/shadow/actions/workflows/ci.yml?query=branch:main+event:push)
[![License](https://img.shields.io/github/license/GradleUp/shadow.svg)](LICENSE)

## Latest Test Compatibility

| Gradle Version | Shadow Version |
|----------------|----------------|
| 5.x            | 5.2.0 - 6.0.0  |
| 6.x            | 5.2.0 - 6.1.0  |
| 7.x            | 7.0.0+         |
| 8.0 - 8.2.x    | 8.0.0 - 8.1.1  |
| 8.3+           | 8.3.0+         |

**NOTE**: Shadow v5.+ is compatible with Gradle 5.x - 6.x and Java 7 - 15 _only_, v6.1.0+ requires Java 8+.
