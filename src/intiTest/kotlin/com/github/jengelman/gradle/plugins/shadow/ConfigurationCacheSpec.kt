package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.doesNotContainEntries
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConfigurationCacheSpec : BasePluginTest() {

  @BeforeAll
  override fun doFirst() {
    super.doFirst()
    publishArtifactA()
    publishArtifactB()
  }

  @BeforeEach
  override fun setup() {
    super.setup()
    projectScriptPath.appendText(
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

    projectScriptPath.appendText(
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
    projectScriptPath.appendText(
      """
        $shadowJar {
          exclude 'a2.properties'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)
    outputShadowJar.deleteExisting()
    val result = run(shadowJarTask)

    assertThat(outputShadowJar).containsEntries(
      "a.properties",
      "b.properties",
    )
    assertThat(outputShadowJar).doesNotContainEntries(
      "a2.properties",
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
    outputServerShadowJar.deleteExisting()
    val result = run(shadowJarTask)

    assertThat(outputServerShadowJar).containsEntries(
      "server/Server.class",
      "junit/framework/Test.class",
    )
    assertThat(outputServerShadowJar).doesNotContainEntries(
      "client/Client.class",
    )
    result.assertCcReused()
  }

  @Test
  fun configurationCachingOfConfigurationsIsUpToDate() {
    settingsScriptPath.appendText(
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
        ${getDefaultProjectBuildScript()}
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
