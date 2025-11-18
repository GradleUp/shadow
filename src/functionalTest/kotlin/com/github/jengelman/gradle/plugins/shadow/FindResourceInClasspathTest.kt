package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import kotlin.io.path.appendText
import org.junit.jupiter.api.Test

class FindResourceInClasspathTest : BasePluginTest() {
  @Test
  fun findResourceInClasspath() {
    projectScript.appendText(
      """
        tasks.register('findResourcesInClasspath', com.github.jengelman.gradle.plugins.shadow.tasks.FindResourceInClasspath) {

        }
      """.trimIndent(),
    )

    val result = runWithSuccess(":findResourcesInClasspath")
    result.output.lines().forEach { println(it) }
  }
}
