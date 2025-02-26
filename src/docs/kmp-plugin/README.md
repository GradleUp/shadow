# Integrating with Kotlin Multiplatform Plugin

Shadow honors Kotlin's
[`org.jetbrains.kotlin.multiplatform`](https://kotlinlang.org/docs/multiplatform-intro.html) plugin and will automatically
configure additional tasks for bundling the shadowed JAR for it's `jvm` target.

```groovy
// Using Shadow with KMP Plugin
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
        implementation "io.ktor:ktor-client-core$ktorVersion"
      }
    }
    jvmMain {
      dependencies {
        implementation "io.ktor:ktor-client-okhttp$ktorVersion"
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
