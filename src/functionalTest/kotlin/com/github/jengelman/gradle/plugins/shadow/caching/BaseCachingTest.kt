package com.github.jengelman.gradle.plugins.shadow.caching

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isInstanceOf
import com.github.jengelman.gradle.plugins.shadow.BasePluginTest
import java.nio.file.NoSuchFileException
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.walk
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

abstract class BaseCachingTest : BasePluginTest() {
  fun assertFirstExecutionSuccess() {
    // task was executed and not pulled from cache
    assertRunWithOutcome(SUCCESS)
  }

  /**
   * This should be called after [assertFirstExecutionSuccess] to ensure that the shadowJar task is cached.
   */
  fun assertExecutionsAreCachedAndUpToDate() {
    run("clean")
    // Make sure the output shadow jar has been deleted.
    assertFailure { outputShadowJar.close() }.isInstanceOf(NoSuchFileException::class)
    @OptIn(ExperimentalPathApi::class)
    val buildDirs = projectRoot.walk().filter { it.isDirectory() && it.name == "build" }
    // Make sure build folders are deleted by clean task.
    assertThat(buildDirs).isEmpty()

    // check that shadowJar pulls from cache in the original directory
    assertRunWithOutcome(FROM_CACHE)
    // check that shadowJar pulls from cache in a different directory
    assertRunWithOutcome(UP_TO_DATE)
  }

  private fun assertRunWithOutcome(expectedOutcome: TaskOutcome) {
    val result = run(shadowJarTask)
    assertThat(result).taskOutcomeEquals(shadowJarTask, expectedOutcome)
  }
}
