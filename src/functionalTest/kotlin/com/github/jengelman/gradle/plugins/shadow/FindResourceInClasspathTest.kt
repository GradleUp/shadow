package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import com.github.jengelman.gradle.plugins.shadow.util.invariantSeparatorsPathString
import kotlin.io.path.appendText
import org.junit.jupiter.api.Test

class FindResourceInClasspathTest : BasePluginTest() {
  @Test
  fun findResourceInClasspath() {
    projectScript.appendText(
      """
        dependencies {
          implementation 'my:a:1.0'
        }
        tasks.register('find1', com.github.jengelman.gradle.plugins.shadow.tasks.FindResourceInClasspath) {
          classpath = configurations.runtimeClasspath
        }
        tasks.register('find2', com.github.jengelman.gradle.plugins.shadow.tasks.FindResourceInClasspath) {
          classpath = configurations.runtimeClasspath
          include("a.properties")
        }
        tasks.register('find3', com.github.jengelman.gradle.plugins.shadow.tasks.FindResourceInClasspath) {
          classpath = configurations.runtimeClasspath
          exclude("a.properties")
        }
      """.trimIndent(),
    )

    val result1 = runWithSuccess(":find1")
    assertThat(result1.output).contains(
      "> Task :find1",
      "scanning ",
      "/my/a/1.0/a-1.0.jar".invariantSeparatorsPathString,
      "/a.properties",
      "/a2.properties",
    )

    val result2 = runWithSuccess(":find2")
    assertThat(result2.output).contains(
      "> Task :find2",
      "scanning ",
      "/my/a/1.0/a-1.0.jar".invariantSeparatorsPathString,
      "/a.properties",
    )
    assertThat(result2.output).doesNotContain(
      "/a2.properties",
    )

    val result3 = runWithSuccess(":find3")
    assertThat(result3.output).contains(
      "> Task :find3",
      "scanning ",
      "/my/a/1.0/a-1.0.jar".invariantSeparatorsPathString,
      "/a2.properties",
    )
    assertThat(result3.output).doesNotContain(
      "/a.properties",
    )
  }
}
