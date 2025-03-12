# Getting Started

=== "Kotlin"

    ```kotlin
    plugins {
      java
      id("com.gradleup.shadow") version "<version>"
    }
    ```

=== "Groovy"

    ```groovy
    plugins {
      id 'java'
      id 'com.gradleup.shadow' version '<version>'
    }
    ```

Alternatively, the plugin can be added to the buildscript classpath and applied:

=== "Kotlin"

    ```kotlin
    buildscript {
      repositories {
        mavenCentral()
        gradlePluginPortal()
      }
      dependencies {
        classpath("com.gradleup.shadow:shadow-gradle-plugin:<version>")
      }
    }
    // `apply plugin` stuffs are used with `buildscript`.
    apply(plugin = "java")
    apply(plugin = "com.gradleup.shadow")
    ```

=== "Groovy"

    ```groovy
    buildscript {
      repositories {
        mavenCentral()
        gradlePluginPortal()
      }
      dependencies {
        classpath 'com.gradleup.shadow:shadow-gradle-plugin:<version>'
      }
    }
    // `apply plugin` stuffs are used with `buildscript`.
    apply plugin: 'java'
    apply plugin: 'com.gradleup.shadow'
    ```

===! "Kotlin"

    <details>
    <summary>Snapshots of the development version are available in 
    <a href="https://oss.sonatype.org/content/repositories/snapshots/com/gradleup/shadow/shadow-gradle-plugin/">
    Sonatype's snapshots repository</a>.
    </summary>
    <p>

    ```kotlin
    buildscript {
      repositories {
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
      }
      dependencies {
        classpath("com.gradleup.shadow:shadow-gradle-plugin:<version>")
      }
    }
    // `apply plugin` stuffs are used with `buildscript`.
    apply(plugin = "java")
    apply(plugin = "com.gradleup.shadow")
    ```

    </p>
    </details>

=== "Groovy"

    <details>
    <summary>Snapshots of the development version are available in 
    <a href="https://oss.sonatype.org/content/repositories/snapshots/com/gradleup/shadow/shadow-gradle-plugin/">
    Sonatype's snapshots repository</a>.
    </summary>
    <p>

    ```groovy
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
    ```

    </p>
    </details>

**NOTE:** The correct maven coordinates for each version of Shadow can be found by referencing the Gradle Plugin documentation [here](https://plugins.gradle.org/plugin/com.gradleup.shadow).

Shadow is a reactive plugin.
This means that applying Shadow by itself will perform no configuration on your project.
Instead, Shadow _reacts_
This means, that for most users, the `java` or `groovy` plugins must be _explicitly_ applied
to have the desired effect.

## Default Java/Kotlin/Groovy Tasks

In the presence of the `java`, `org.jetbrains.kotlin.jvm` or `groovy` plugins 
(that applied [JavaPlugin](https://docs.gradle.org/current/userguide/java_plugin.html) in their build logic), 
Shadow will automatically configure the following behavior:

* Adds a `shadowJar` task to the project.
* Adds a `shadow` configuration to the project.
* Adds a `shadow` variant to the project.
* Adds a `shadow` component to the project.
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
