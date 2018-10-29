# Getting Started

```groovy no-plugins
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'com.github.jengelman.gradle.plugins:shadow:{project-version}'
    }
}

apply plugin: 'com.github.johnrengelman.shadow'
apply plugin: 'java'
```

Alternatively, the Gradle Plugin syntax can be used:

```groovy no-plugins
plugins {
  id 'com.github.johnrengelman.shadow' version '{project-version}'
  id 'java'
}
```

Shadow is a reactive plugin.
This means that applying Shadow by itself will perform no configuration on your project.
Instead, Shadow _reacts_
This means, that for most users, the `java` or `groovy` plugins must be _explicitly_ applied
to have the desired effect.

## Default Java/Groovy Tasks

In the presence of the `java` or `groovy` plugins, Shadow will automatically configure the
following behavior:

* Adds a `shadowJar` task to the project.
* Adds a `shadow` configuration to the project.
* Configures the `shadowJar` task to include all sources from the project's `main` sourceSet.
* Configures the `shadowJar` task to bundle all dependencies from the `runtime` configuration.
* Configures the _classifier_ attribute of the `shadowJar` task to be `'all'` .
* Configures the `shadowJar` task to generate a `Manifest` with:
  * Inheriting all configuration from the standard `jar` task.
  * Adds a `Class-Path` attribute to the `Manifest` that appends all dependencies from the `shadow` configuration
* Configures the `shadowJar` task to _exclude_ any JAR index or cryptographic signature files matching the following patterns:
  * `META-INF/INDEX.LIST`
  * `META-INF/*.SF`
  * `META-INF/*.DSA`
  * `META-INF/*.RSA`
* Creates and registers the `shadow` component in the project (used for integrating with `maven-publish`).
* Configures the `uploadShadow` task (as part of the `maven` plugin) with the following behavior:
** Removes the `compile` and `runtime` configurations from the `pom.xml` file mapping.
** Adds the `shadow` configuration to the `pom.xml` file as `RUNTIME` scope.

## Shadowing Gradle Plugins

Shadow ships with a companion task that can be used to automatically discover dependency packages and configure 
them for relocation. This is useful in projects if you want to relocate all dependencies.

For more details see the section [Using Shadow to Package Gradle Plugins](/plugins/)
