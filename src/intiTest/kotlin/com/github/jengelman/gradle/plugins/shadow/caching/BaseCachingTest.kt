package com.github.jengelman.gradle.plugins.shadow.caching

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.github.jengelman.gradle.plugins.shadow.BasePluginTest
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.appendText
import kotlin.io.path.copyToRecursively
import kotlin.io.path.deleteRecursively
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir

abstract class BaseCachingTest : BasePluginTest() {

  @TempDir
  lateinit var alternateDir: Path

  @BeforeEach
  override fun setup() {
    super.setup()
    // Use a test-specific build cache directory. This ensures that we'll only use cached outputs generated during
    // this test, and we won't accidentally use cached outputs from a different test or a different build.
    settingsScriptPath.appendText(
      """
        buildCache {
          local {
            directory = file('build-cache')
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
    // TODO: Use PluginSpecification.run here to reuse flags, but cache tests failed for now, need to investigate.
    runnerBlock(getRunner().withArguments("--build-cache", shadowJarTask))
      .build()
      .assert(expectedOutcome)
  }

  @OptIn(ExperimentalPathApi::class)
  fun assertShadowJarIsCachedAndRelocatable() {
    try {
      outputShadowJar.deleteExisting()
    } catch (ignored: IllegalStateException) {
      // ignore if the file does not exist
    }
    alternateDir.deleteRecursively()
    root.copyToRecursively(alternateDir, followLinks = false, overwrite = false)
    // check that shadowJar pulls from cache in the original directory
    assertShadowJarHasResult(FROM_CACHE)
    // check that shadowJar pulls from cache in a different directory
    assertShadowJarHasResult(FROM_CACHE) {
      if (alternateDir.listDirectoryEntries().isEmpty()) {
        error("Directory was not copied to alternate directory")
      }
      it.withProjectDir(alternateDir.toFile())
    }
  }

  fun assertShadowJarExecutes() {
    try {
      outputShadowJar.deleteExisting()
    } catch (ignored: IllegalStateException) {
      // ignore if the file does not exist
    }
    // task was executed and not pulled from cache
    assertShadowJarHasResult(SUCCESS)
  }

  private fun BuildResult.assert(expectedOutcome: TaskOutcome) {
    assertThat(task(shadowJarTask)).isNotNull()
      .transform { it.outcome }.isEqualTo(expectedOutcome)
  }
}
