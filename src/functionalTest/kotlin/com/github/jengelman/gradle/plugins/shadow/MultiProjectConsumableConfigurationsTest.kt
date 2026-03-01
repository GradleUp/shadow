package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.testkit.containsAtLeast
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test

class MultiProjectConsumableConfigurationsTest : BasePluginTest() {

  @Test
  fun consumeShadowedProjectViaApiElementsAndRuntimeElementsWithGroovyDsl() {
    settingsScript.appendText(
      """
      include 'client', 'server'
      """
        .trimIndent()
    )
    projectScript.writeText("")

    path("client/src/main/java/client/Client.java")
      .writeText(
        """
        package client;
        public class Client {}
        """
          .trimIndent()
      )
    path("client/build.gradle")
      .writeText(
        """
      ${getDefaultProjectBuildScript("java-library")}
      dependencies {
        api 'junit:junit:3.8.2'
      }
      $shadowJarTask {
        relocate 'junit.framework', 'client.junit.framework'
      }
      configurations {
        apiElements {
          outgoing.artifacts.clear()
          outgoing.artifact(tasks.shadowJar)
          // Ensure Gradle doesn't select the `classes` variant
          outgoing.variants.clear()
        }
        runtimeElements {
          outgoing.artifacts.clear()
          outgoing.artifact(tasks.shadowJar)
          outgoing.variants.clear()
        }
      }
      """
          .trimIndent() + lineSeparator
      )

    path("server/src/main/java/server/Server.java")
      .writeText(
        """
        package server;
        import client.Client;
        import client.junit.framework.Test;
        public class Server {}
        """
          .trimIndent()
      )
    path("server/build.gradle")
      .writeText(
        """
      ${getDefaultProjectBuildScript("java")}
      dependencies {
        // Look ma, no `configuration: "shadow"` needed!
        implementation project(':client')
      }
      tasks.named("compileJava") {
        doFirst {
          println "CLASSPATH IS: " + classpath.files
        }
      }
      """
          .trimIndent() + lineSeparator
      )

    // Running server:jar to ensure it compiles against the shadowed client
    runWithSuccess(":server:jar")

    // The fact that server compiled successfully against `client.junit.framework.Test`
    // means it consumed the shadowed artifact during compilation.
    assertThat(jarPath("server/build/libs/server-1.0.jar")).useAll {
      containsAtLeast("server/Server.class")
    }
  }

  @Test
  fun consumeShadowedProjectViaApiElementsAndRuntimeElementsWithKotlinDsl() {
    path("settings.gradle.kts")
      .writeText(
        """
      include("client", "server")

      dependencyResolutionManagement {
        repositories {
          maven { url = uri("${localRepo.root.toUri()}") }
          mavenCentral()
        }
      }
      buildCache {
        local { directory = file("build-cache") }
      }
      enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
      rootProject.name = "my"
      """
          .trimIndent()
      )
    path("settings.gradle").toFile().delete()

    path("client/src/main/java/client/Client.java")
      .writeText(
        """
        package client;
        public class Client {}
        """
          .trimIndent()
      )

    path("client/build.gradle.kts")
      .writeText(
        """
      plugins {
        id("java-library")
        id("$shadowPluginId")
      }
      group = "my"
      version = "1.0"

      dependencies {
        api("junit:junit:3.8.2")
      }

      tasks.shadowJar {
        relocate("junit.framework", "client.junit.framework")
      }

      configurations {
        named("apiElements") {
          outgoing.artifacts.clear()
          outgoing.artifact(tasks.shadowJar)
          // Ensure Gradle doesn't select the `classes` variant
          outgoing.variants.clear()
        }
        named("runtimeElements") {
          outgoing.artifacts.clear()
          outgoing.artifact(tasks.shadowJar)
          outgoing.variants.clear()
        }
      }
      """
          .trimIndent()
      )

    path("server/src/main/java/server/Server.java")
      .writeText(
        """
        package server;
        import client.Client;
        import client.junit.framework.Test;
        public class Server {}
        """
          .trimIndent()
      )
    path("server/build.gradle.kts")
      .writeText(
        """
      plugins {
        id("java")
        id("$shadowPluginId")
      }
      group = "my"
      version = "1.0"

      dependencies {
        implementation(project(":client"))
      }
      """
          .trimIndent()
      )

    // Run server:jar, which requires compiling server/Server.java against the relocated junit Test
    // class
    runWithSuccess(":server:jar")

    assertThat(jarPath("server/build/libs/server-1.0.jar")).useAll {
      containsAtLeast("server/Server.class")
    }
  }
}
