# Getting Started

    plugins {
      id 'java'
      id 'com.gradleup.shadow' version '<version>'
    }

Alternatively, the plugin can be added to the buildscript classpath and applied:

    buildscript {
      repositories {
        gradlePluginPortal()
      }
      dependencies {
        classpath 'com.gradleup.shadow:shadow-gradle-plugin:<version>'
      }
    }
    
    // `apply plugin` stuffs are used with `buildscript`.
    apply plugin: 'java'
    apply plugin: 'com.gradleup.shadow'

<details>
<summary>Snapshots of the development version are available in 
<a href="https://oss.sonatype.org/content/repositories/snapshots/com/gradleup/shadow/shadow-gradle-plugin/">
Sonatype's snapshots repository</a>.
</summary>
<p>

    buildscript {
      repositories {
        mavenCentral()
        maven { url = 'https://oss.sonatype.org/content/repositories/snapshots/' }
      }
      dependencies {
        classpath 'com.gradleup.shadow:shadow-gradle-plugin:<version>'
      }
    }
    
    // `apply plugin` stuffs are used with `buildscript`.
    apply plugin: 'java'
    apply plugin: 'com.gradleup.shadow'

</p>
</details>

**NOTE:** The correct maven coordinates for each version of Shadow can be found by referencing the Gradle Plugin documentation [here](https://plugins.gradle.org/plugin/com.gradleup.shadow).

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
* Configures the `shadowJar` task to bundle all dependencies from the `runtimeClasspath` configuration.
* Configures the _classifier_ attribute of the `shadowJar` task to be `'all'` .
* Configures the `shadowJar` task to generate a `Manifest` with:
    * Inheriting all configuration from the standard `jar` task.
    * Adds a `Class-Path` attribute to the `Manifest` that appends all dependencies from the `shadow` configuration
* Configures the `shadowJar` task to _exclude_ any JAR index or cryptographic signature files matching the following patterns:
    * `META-INF/INDEX.LIST`
    * `META-INF/*.SF`
    * `META-INF/*.DSA`
    * `META-INF/*.RSA`
    * `META-INF/versions/**/module-info.class`
    * `module-info.class`
* Creates and registers the `shadow` component in the project (used for integrating with `maven-publish`).

## Shadowing Gradle Plugins

Shadow ships with a companion task that can be used to automatically discover dependency packages and configure 
them for relocation. This is useful in projects if you want to relocate all dependencies.

For more details see the section [Using Shadow to Package Gradle Plugins](/plugins/)
