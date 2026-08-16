package com.github.jengelman.gradle.plugins.shadow

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import com.github.jengelman.gradle.plugins.shadow.tasks.FindResourceInClasspath
import com.github.jengelman.gradle.plugins.shadow.testkit.runTests
import com.github.jengelman.gradle.plugins.shadow.testkit.variantSeparatorsPathString
import de.infix.testBalloon.framework.core.testSuite
import kotlin.io.path.appendText

val FindResourceInClasspathTests by testSuite {
  runTests(::FindResourceInClasspathTest)
}

private class FindResourceInClasspathTest : BasePluginTest() {
  fun findResourceInClasspath() {
    val taskClassName = FindResourceInClasspath::class.java.name
    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'my:a:1.0'
      |}
      |tasks.register('find1', $taskClassName) {
      |  classpath = configurations.runtimeClasspath
      |}
      |tasks.register('find2', $taskClassName) {
      |  classpath = configurations.runtimeClasspath
      |  include("a.properties")
      |}
      |tasks.register('find3', $taskClassName) {
      |  classpath = configurations.runtimeClasspath
      |  exclude("a.properties")
      |}
      """
        .trimMargin()
    )

    assertThat(runWithSuccess(":find1").output)
      .contains(
        "> Task :find1",
        "scanning ",
        "/my/a/1.0/a-1.0.jar".variantSeparatorsPathString,
        "/a.properties".variantSeparatorsPathString,
        "/a2.properties".variantSeparatorsPathString,
      )

    assertThat(runWithSuccess(":find2").output).all {
      contains(
        "> Task :find2",
        "scanning ",
        "/my/a/1.0/a-1.0.jar".variantSeparatorsPathString,
        "/a.properties".variantSeparatorsPathString,
      )
      doesNotContain("/a2.properties".variantSeparatorsPathString)
    }

    assertThat(runWithSuccess(":find3").output).all {
      contains(
        "> Task :find3",
        "scanning ",
        "/my/a/1.0/a-1.0.jar".variantSeparatorsPathString,
        "/a2.properties".variantSeparatorsPathString,
      )
      doesNotContain("/a.properties".variantSeparatorsPathString)
    }
  }
}
