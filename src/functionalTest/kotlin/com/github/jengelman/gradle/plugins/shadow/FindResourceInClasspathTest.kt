package com.github.jengelman.gradle.plugins.shadow

import assertk.all
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

    assertThat(runWithSuccess(":find1").output).contains(
      "> Task :find1",
      "scanning ",
      "/my/a/1.0/a-1.0.jar".invariantSeparatorsPathString,
      "/a.properties".invariantSeparatorsPathString,
      "/a2.properties".invariantSeparatorsPathString,
    )

    assertThat(runWithSuccess(":find2").output).all {
      contains(
        "> Task :find2",
        "scanning ",
        "/my/a/1.0/a-1.0.jar".invariantSeparatorsPathString,
        "/a.properties".invariantSeparatorsPathString,
      )
      doesNotContain(
        "/a2.properties".invariantSeparatorsPathString,
      )
    }

    assertThat(runWithSuccess(":find3").output).all {
      contains(
        "> Task :find3",
        "scanning ",
        "/my/a/1.0/a-1.0.jar".invariantSeparatorsPathString,
        "/a2.properties".invariantSeparatorsPathString,
      )
      doesNotContain(
        "/a.properties".invariantSeparatorsPathString,
      )
    }
  }
}
