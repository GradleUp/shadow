# Integrating with Kotlin Multiplatform Plugin

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
      jvm()
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
      jvm()
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
