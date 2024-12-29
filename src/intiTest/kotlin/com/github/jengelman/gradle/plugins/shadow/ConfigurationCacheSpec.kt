package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.exists
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import kotlin.io.path.appendText
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConfigurationCacheSpec : BasePluginTest() {

  @BeforeEach
  override fun setup() {
    super.setup()
    publishArtifactA()
    publishArtifactB()
    buildScript.appendText(
      """
        dependencies {
          implementation 'shadow:a:1.0'
          implementation 'shadow:b:1.0'
        }
      """.trimIndent() + System.lineSeparator(),
    )
  }

  @Test
  fun supportsConfigurationCache() {
    path("src/main/java/myapp/Main.java").writeText(
      """
        package myapp;
        public class Main {
          public static void main(String[] args) {
            System.out.println("TestApp: Hello World! (" + args[0] + ")");
          }
        }
      """.trimIndent(),
    )

    buildScript.appendText(
      """
        apply plugin: 'application'

        application {
          mainClass = 'myapp.Main'
        }
        $runShadow {
          args 'foo'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)
    val result = run(shadowJarTask)

    result.assertCcReused()
  }

  @Test
  fun configurationCachingSupportsExcludes() {
    buildScript.appendText(
      """
        $shadowJar {
          exclude 'a2.properties'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)
    outputShadowJar.deleteExisting()
    val result = run(shadowJarTask)

    assertContains(
      outputShadowJar,
      listOf("a.properties", "b.properties"),
    )
    assertDoesNotContain(
      outputShadowJar,
      listOf("a2.properties"),
    )
    result.assertCcReused()
  }

  @Test
  fun configurationCachingSupportsMinimize() {
    writeClientAndServerModules(
      serverShadowBlock = """
        minimize {
          exclude(dependency('junit:junit:.*'))
        }
      """.trimIndent(),
    )

    run(shadowJarTask)
    val output = path("server/build/libs/server-1.0-all.jar")
    output.deleteExisting()
    val result = run(shadowJarTask)

    assertThat(output).exists()
    assertContains(
      output,
      listOf("server/Server.class", "junit/framework/Test.class"),
    )
    assertDoesNotContain(
      output,
      listOf("client/Client.class"),
    )
    result.assertCcReused()
  }

  @Test
  fun configurationCachingOfConfigurationsIsUpToDate() {
    settingsScript.appendText(
      """
        include 'lib'
      """.trimIndent(),
    )

    path("lib/src/main/java/lib/Lib.java").writeText(
      """
        package lib;
        public class Lib {}
      """.trimIndent(),
    )
    path("lib/build.gradle").writeText(
      """
        ${getProjectBuildScript()}
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
        $shadowJar {
          configurations = [project.configurations.compileClasspath]
        }
      """.trimIndent(),
    )

    run(shadowJarTask)
    val result = run(shadowJarTask)

    assertThat(result.task(":lib:shadowJar")).isNotNull()
      .transform { it.outcome }.isEqualTo(TaskOutcome.UP_TO_DATE)
    result.assertCcReused()
  }

  private fun BuildResult.assertCcReused() {
    assertThat(output).contains("Reusing configuration cache.")
  }
}
