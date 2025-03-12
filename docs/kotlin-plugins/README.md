# Integrating with Kotlin Plugins

Kotlin standard libraries (stdlib) are added by Kotlin plugins by default, they will be bundled into the shadowed JARs automatically.
If you don't need a standard library at all, you can add the following Gradle property to your gradle.properties file:

```properties
kotlin.stdlib.default.dependency=false
```

See more information about [Dependency on the standard library](https://kotlinlang.org/docs/gradle-configure-project.html#dependency-on-the-standard-library).

## For Kotlin JVM Plugin

Shadow works well for Kotlin JVM projects like Java projects. Here is an example:

=== "Kotlin"

    ```kotlin
    plugins {
      kotlin("jvm")
      id("com.gradleup.shadow")
    }

    dependencies {
      implementation("io.ktor:ktor-client-okhttp:3.1.0")
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
      id 'org.jetbrains.kotlin.jvm'
      id 'com.gradleup.shadow'
    }

    dependencies {
      implementation 'io.ktor:ktor-client-okhttp:3.1.0'
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      manifest {
        // Optionally, set the main class for the shadowed JAR.
        attributes 'Main-Class': 'com.example.MainKt'
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
            implementation 'io.ktor:ktor-client-core:$ktorVersion'
          }
        }
        jvmMain {
          dependencies {
            implementation 'io.ktor:ktor-client-okhttp:$ktorVersion'
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
