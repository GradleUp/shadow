package com.github.jengelman.gradle.plugins.shadow.caching

import assertk.Assert
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isInstanceOf
import com.github.jengelman.gradle.plugins.shadow.BasePluginTest
import com.github.jengelman.gradle.plugins.shadow.util.JarPath
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
  open val taskPath: String = shadowJarTask

  fun assertExecutionSuccess(vararg outputs: String) {
    // task was executed and not pulled from cache
    assertRunWithResult(SUCCESS, outputs = outputs)
  }

  /**
   * This should be called after [assertExecutionSuccess] to ensure that the [taskPath] is cached.
   */
  fun assertExecutionsFromCacheAndUpToDate(vararg outputs: String) {
    run("clean")
    // Make sure the output shadow jar has been deleted.
    assertFailure { outputShadowJar.close() }.isInstanceOf(NoSuchFileException::class)
    @OptIn(ExperimentalPathApi::class)
    val buildDirs = projectRoot.walk().filter { it.isDirectory() && it.name == "build" }
    // Make sure build folders are deleted by clean task.
    assertThat(buildDirs).isEmpty()

    // Run the task again to ensure it is pulled from cache.
    assertRunWithResult(FROM_CACHE, outputs = outputs)
    // Run the task again to ensure it is up-to-date.
    assertRunWithResult(UP_TO_DATE, outputs = outputs)
  }

  fun assertCompositeExecutions(
    vararg outputs: String,
    jarPathProvider: () -> JarPath = { outputShadowJar },
    jarPathAssertions: Assert<JarPath>.() -> Unit = {},
  ) {
    assertExecutionSuccess()
    assertThat(jarPathProvider()).useAll(jarPathAssertions)
    assertExecutionsFromCacheAndUpToDate(outputs = outputs)
  }

  private fun assertRunWithResult(expectedOutcome: TaskOutcome, vararg outputs: String) {
    val result = run(taskPath)
    assertThat(result).taskOutcomeEquals(taskPath, expectedOutcome)
    assertThat(result.output).contains(*outputs)
  }
}
