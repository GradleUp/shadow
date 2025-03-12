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
      id 'groovy'
      id 'com.gradleup.shadow'
    }

    dependencies {
      implementation 'org.scala-lang:scala-library:2.13.16'
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      manifest {
        // Optionally, set the main class for the shadowed JAR.
        attributes 'Main-Class': 'com.example.Main'
      }
    }
    ```
