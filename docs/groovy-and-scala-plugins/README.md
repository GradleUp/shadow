# Integrating with Groovy and Scala Plugins

Shadow also works well for Groovy and Scala, here are examples:

For Groovy:

=== "Kotlin"

    ```kotlin
    plugins {
      groovy
      id("com.gradleup.shadow")
    }

    dependencies {
      // If you don't want the Groovy standard library to be shadowed, please replace `implementation` with `api`.
      implementation(localGroovy())
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
      id 'groovy'
      id 'com.gradleup.shadow'
    }

    dependencies {
      // If you don't want the Groovy standard library to be shadowed, please replace `implementation` with `api`.
      implementation localGroovy()
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      manifest {
        // Optionally, set the main class for the shadowed JAR.
        attributes 'Main-Class': 'com.example.Main'
      }
    }
    ```

For Scala:

=== "Kotlin"

    ```kotlin
    plugins {
      scala
      id("com.gradleup.shadow")
    }

    dependencies {
      // If you don't want the Scala standard library to be shadowed, please replace `implementation` with `api`.
      implementation("org.scala-lang:scala-library:2.13.16")
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
      id 'scala'
      id 'com.gradleup.shadow'
    }

    dependencies {
      // If you don't want the Scala standard library to be shadowed, please replace `implementation` with `api`.
      implementation 'org.scala-lang:scala-library:2.13.16'
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      manifest {
        // Optionally, set the main class for the shadowed JAR.
        attributes 'Main-Class': 'com.example.Main'
      }
    }
    ```
