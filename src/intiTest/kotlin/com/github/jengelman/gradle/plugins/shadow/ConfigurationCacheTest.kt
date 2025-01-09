package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.doesNotContainEntries
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConfigurationCacheTest : BasePluginTest() {
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
    writeMainClass()

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

    run(serverShadowJarTask)
    outputServerShadowJar.deleteExisting()
    val result = run(serverShadowJarTask)

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

    val libShadowJarTask = ":lib:$SHADOW_JAR_TASK_NAME"
    run(libShadowJarTask)
    val result = run(libShadowJarTask)

    assertThat(result.task(libShadowJarTask)).isNotNull()
      .transform { it.outcome }.isEqualTo(TaskOutcome.UP_TO_DATE)
    result.assertCcReused()
  }

  private fun BuildResult.assertCcReused() {
    assertThat(output).contains("Reusing configuration cache.")
  }
}
