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
    <summary>Snapshots of the development version are available in Central Portal Snapshots.
    </summary>
    <p>

    ```kotlin
    buildscript {
      repositories {
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
      }
      dependencies {
        // You can get the latest snapshot version from `VERSION_NAME` declared in https://github.com/GradleUp/shadow/blob/main/gradle.properties
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
    <summary>Snapshots of the development version are available in Central Portal Snapshots.
    </summary>
    <p>

    ```groovy
    buildscript {
      repositories {
        mavenCentral()
        maven { url = 'https://central.sonatype.com/repository/maven-snapshots/' }
      }
      dependencies {
        // You can get the latest snapshot version from `VERSION_NAME` declared in https://github.com/GradleUp/shadow/blob/main/gradle.properties
        classpath 'com.gradleup.shadow:shadow-gradle-plugin:<version>'
      }
    }
    // `apply plugin` stuffs are used with `buildscript`.
    apply plugin: 'java'
    apply plugin: 'com.gradleup.shadow'
    ```

    </p>
    </details>

**NOTE:** The correct maven coordinates for each version of Shadow can be found by referencing the Gradle Plugin
documentation [here](https://plugins.gradle.org/plugin/com.gradleup.shadow).

Shadow is a reactive plugin.
This means that applying Shadow by itself will perform no configuration on your project.
Instead, Shadow _reacts_
This means, that for most users, the `java` or `groovy` plugins must be _explicitly_ applied
to have the desired effect.

## Default Java/Kotlin/Groovy Tasks

In the presence of the `java`, `org.jetbrains.kotlin.jvm` or `groovy` plugins (that apply [`JavaPlugin`][JavaPlugin]
in their build logic), Shadow will automatically configure the following behavior:

* Adds a [`ShadowJar`][ShadowJar] task to the project.
* Adds a `shadow` configuration to the project.
* Adds a `shadow` variant to the project.
* Adds a `shadow` component to the project.
* Configures the [`ShadowJar`][ShadowJar] task to include all sources from the project's `main` sourceSet.
* Configures the [`ShadowJar`][ShadowJar] task to bundle all dependencies from the `runtimeClasspath` configuration.
* Configures the _classifier_ attribute of the [`ShadowJar`][ShadowJar] task to be `'all'` .
* Configures the [`ShadowJar`][ShadowJar] task to generate a `Manifest` with:
    * Inheriting all configuration from the standard [`Jar`][Jar] task.
    * Adds a `Class-Path` attribute to the `Manifest` that appends all dependencies from the `shadow` configuration
* Configures the [`ShadowJar`][ShadowJar] task to _exclude_ any JAR index or cryptographic signature files matching the
  following patterns:
    * `META-INF/INDEX.LIST`
    * `META-INF/*.SF`
    * `META-INF/*.DSA`
    * `META-INF/*.RSA`
    * `META-INF/versions/**/module-info.class`
    * `module-info.class`
* Creates and registers the `shadow` component in the project (used for integrating with
  [`maven-publish`][maven-publish]).

## ShadowJar Command Line options

Sometimes, a user wants to declare the value of an exposed task property on the command line instead of the
build script. Passing property values on the command line is particularly helpful if they change more frequently.  
Here are the options that can be passed to the `shadowJar`:

```
--enable-auto-relocation          Enables auto relocation of packages in the dependencies.
--no-enable-auto-relocation       Disables option --enable-auto-relocation.
--fail-on-duplicate-entries       Fail build if the ZIP entries in the shadowed JAR are duplicated.
--no-fail-on-duplicate-entries    Disables option --fail-on-duplicate-entries.
--minimize-jar                    Minimizes the jar by removing unused classes.
--no-minimize-jar                 Disables option --minimize-jar.
--relocation-prefix               Prefix used for auto relocation of packages in the dependencies.
--rerun                           Causes the task to be re-run even if up-to-date.
```

Also, you can view more information about the [`ShadowJar`][ShadowJar] task by running the following command:

```sh
./gradlew -q help --task shadowJar
```

Refer to
[listing command line options](https://docs.gradle.org/current/userguide/custom_tasks.html#sec:listing_task_options).



[Jar]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Jar.html
[JavaPlugin]: https://docs.gradle.org/current/userguide/java_plugin.html
[maven-publish]: https://docs.gradle.org/current/userguide/publishing_maven.html
[ShadowJar]: ../api/shadow/com.github.jengelman.gradle.plugins.shadow.tasks/-shadow-jar/index.html
