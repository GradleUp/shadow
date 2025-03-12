# Integrating with Kotlin Plugins

## For Kotlin JVM Plugin

Shadow works well for Kotlin JVM projects like Java projects. Here is an example:

=== "Kotlin"

    ```kotlin
    plugins {
      kotlin("jvm")
      id("com.gradleup.shadow")
    }

    dependencies {
      // kotlin-stdlib is added automatically by Kotlin plugin.
    }

    tasks.shadowJar {
      manifest {
        // Optionally, set the main class for the shadowed JAR.
        attributes["Main-Class"] = "com.example.Main"
      }
    }
    ```

=== "Groovy"

    ```groovy
    plugins {
      id 'org.jetbrains.kotlin.jvm'
      id 'com.gradleup.shadow'
    }

    dependencies {
      // kotlin-stdlib is added automatically by Kotlin plugin.
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      manifest {
        // Optionally, set the main class for the shadowed JAR.
        attributes 'Main-Class': 'com.example.Main'
      }
    }
    ```

You can mix Kotlin JVM plugin using with other Java plugins like `java-gradle-plugin`, `application`, and other plugins,
easily organize your build logic for [Packaging Gradle Plugins](../plugins/README.md), [Publishing Libraries](../publishing/README.md),
[Running Applications](../application-plugin/README.md), and so on.

## For Kotlin Multiplatform Plugin

Shadow honors Kotlin's
[`org.jetbrains.kotlin.multiplatform`](https://kotlinlang.org/docs/multiplatform-intro.html) plugin and will automatically
configure additional tasks for bundling the shadowed JAR for its `jvm` target.

=== "Kotlin"

    ```kotlin
    plugins {
      kotlin("multiplatform")
      id("com.gradleup.shadow")
    }

    val ktorVersion = "3.1.0"

    kotlin {
      jvm().mainRun {
        // Optionally, set the main class for `runJvm`, it's available from Kotlin 2.1.0
        mainClass = "myapp.MainKt"
      }
      sourceSets {
        val commonMain by getting {
          dependencies {
            implementation("io.ktor:ktor-client-core:$ktorVersion")
          }
        }
        val jvmMain by getting {
          dependencies {
            implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
          }
        }
      }
    }

    tasks.shadowJar {
      manifest {
        // Optionally, set the main class for the shadowed JAR.
        attributes["Main-Class"] = "com.example.MainKt"
      }
    }
    ```

=== "Groovy"

    ```groovy
    plugins {
      id 'org.jetbrains.kotlin.multiplatform'
      id 'com.gradleup.shadow'
    }

    def ktorVersion = "3.1.0"

    kotlin {
      jvm().mainRun {
        // Optionally, set the main class for `runJvm`, it's available from Kotlin 2.1.0
        it.mainClass.set('myapp.MainKt')
      }
      sourceSets {
        commonMain {
          dependencies {
            implementation "io.ktor:ktor-client-core:$ktorVersion"
          }
        }
        jvmMain {
          dependencies {
            implementation "io.ktor:ktor-client-okhttp:$ktorVersion"
          }
        }
      }
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      manifest {
        // Optionally, set the main class for the shadowed JAR.
        attributes 'Main-Class': 'com.example.MainKt'
      }
    }
    ```
