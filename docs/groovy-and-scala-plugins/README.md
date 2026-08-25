# Integrating with Groovy and Scala Plugins

Shadow also works well for Groovy and Scala, here are integration examples:

For Groovy:

=== "build.gradle.kts"

    ```kotlin
    plugins {
      groovy
      id("com.gradleup.shadow")
    }

    tasks.shadowJar {
      manifest {
        // Optionally, set the main class for the shadowed JAR.
        attributes["Main-Class"] = "com.example.Main"
      }
    }
    ```

=== "build.gradle"

    ```groovy
    plugins {
      id 'groovy'
      id 'com.gradleup.shadow'
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      manifest {
        // Optionally, set the main class for the shadowed JAR.
        attributes 'Main-Class': 'com.example.Main'
      }
    }
    ```

For Scala:

=== "build.gradle.kts"

    ```kotlin
    plugins {
      scala
      id("com.gradleup.shadow")
    }

    tasks.shadowJar {
      manifest {
        // Optionally, set the main class for the shadowed JAR.
        attributes["Main-Class"] = "com.example.Main"
      }
    }
    ```

=== "build.gradle"

    ```groovy
    plugins {
      id 'scala'
      id 'com.gradleup.shadow'
    }

    tasks.named('shadowJar', com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar) {
      manifest {
        // Optionally, set the main class for the shadowed JAR.
        attributes 'Main-Class': 'com.example.Main'
      }
    }
    ```

You can customize the other configurations of the `shadowJar` task as needed, just like with Java projects.
