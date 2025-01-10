package com.github.jengelman.gradle.plugins.shadow.caching

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.BasePluginTest
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.deleteRecursively
import kotlin.io.path.listDirectoryEntries
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE
import org.junit.jupiter.api.io.TempDir

abstract class BaseCachingTest : BasePluginTest() {
  @TempDir
  lateinit var alternateDir: Path

  @OptIn(ExperimentalPathApi::class)
  fun assertShadowJarIsCachedAndRelocatable(
    firstOutcome: TaskOutcome = FROM_CACHE,
    secondOutcome: TaskOutcome = UP_TO_DATE,
  ) {
    try {
      outputShadowJar.deleteExisting()
    } catch (ignored: NoSuchFileException) {
    }
    alternateDir.deleteRecursively()
    projectRoot.copyToRecursively(target = alternateDir, followLinks = false)
    // check that shadowJar pulls from cache in the original directory
    assertShadowJarHasResult(firstOutcome)
    // check that shadowJar pulls from cache in a different directory
    assertShadowJarHasResult(secondOutcome) {
      if (alternateDir.listDirectoryEntries().isEmpty()) {
        error("Directory was not copied to alternate directory")
      }
      it.withProjectDir(alternateDir.toFile())
    }
  }

  fun assertShadowJarExecutes() {
    try {
      outputShadowJar.deleteExisting()
    } catch (ignored: NoSuchFileException) {
    }
    // task was executed and not pulled from cache
    assertShadowJarHasResult(SUCCESS)
  }

  private fun assertShadowJarHasResult(
    expectedOutcome: TaskOutcome,
    runnerBlock: (GradleRunner) -> GradleRunner = { it },
  ) {
    val result = run(shadowJarTask, runnerBlock = runnerBlock)
    assertThat(result).taskOutcomeEquals(shadowJarTask, expectedOutcome)
  }
}
