package com.github.jengelman.gradle.plugins.shadow.caching

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.github.jengelman.gradle.plugins.shadow.BasePluginTest
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.appendText
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir

abstract class BaseCachingTest : BasePluginTest() {

  @TempDir
  lateinit var alternateDir: Path

  @BeforeEach
  override fun setup() {
    super.setup()
    settingsScriptPath.appendText(
      """
        buildCache {
          local {
            directory = file("build-cache")
          }
        }
      """.trimIndent() + System.lineSeparator(),
    )
  }

  fun changeConfigurationTo(content: String) {
    projectScriptPath.writeText(getDefaultProjectBuildScript("java", withGroup = true, withVersion = true))
    projectScriptPath.appendText(content)
  }

  fun assertShadowJarHasResult(
    expectedOutcome: TaskOutcome,
    runnerBlock: (GradleRunner) -> GradleRunner = { it },
  ) {
    run("--build-cache", shadowJarTask, runnerBlock = runnerBlock).assert(expectedOutcome)
  }

  @OptIn(ExperimentalPathApi::class)
  fun copyToAlternateDir() {
    alternateDir.deleteRecursively()
    alternateDir.createDirectories()
    root.copyTo(alternateDir)
  }

  fun assertShadowJarIsCachedAndRelocatable() {
    outputShadowJar.deleteIfExists()
    copyToAlternateDir()
    assertShadowJarHasResult(TaskOutcome.FROM_CACHE)
    assertShadowJarHasResult(TaskOutcome.FROM_CACHE) {
      it.withProjectDir(alternateDir.toFile())
    }
  }

  fun assertShadowJarExecutes() {
    outputShadowJar.deleteIfExists()
    assertShadowJarHasResult(TaskOutcome.SUCCESS)
  }

  private fun BuildResult.assert(expectedOutcome: TaskOutcome) {
    assertThat(task(shadowJarTask)).isNotNull()
      .transform { it.outcome }.isEqualTo(expectedOutcome)
  }
}
