package com.github.jengelman.gradle.plugins.shadow.testkit

import assertk.assertThat
import assertk.assertions.doesNotContain
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

private val testKitDir by lazy {
  val gradleUserHome =
    System.getenv("GRADLE_USER_HOME")
      ?: Path(System.getProperty("user.home"), ".gradle").absolutePathString()
  Path(gradleUserHome, "testkit")
}

private val testGradleVersion by lazy {
  System.getProperty("TEST_GRADLE_VERSION")
    ?: error("TEST_GRADLE_VERSION system property is not set.")
}

val commonGradleArgs =
  setOf(
    "--configuration-cache",
    "--build-cache",
    "--parallel",
    "--stacktrace",
    // https://docs.gradle.org/current/userguide/configuration_cache.html#config_cache:usage:parallel
    "-Dorg.gradle.configuration-cache.parallel=true",
  )

fun gradleRunner(
  projectDir: Path,
  arguments: Iterable<String>,
  warningsAsErrors: Boolean = true,
  block: GradleRunner.() -> Unit = {},
): GradleRunner =
  GradleRunner.create()
    .withGradleVersion(testGradleVersion)
    .forwardOutput()
    .withPluginClasspath()
    .withTestKitDir(testKitDir.toFile())
    .withArguments(
      buildList {
        addAll(arguments)
        if (warningsAsErrors) {
          add("--warning-mode=fail")
        }
      }
    )
    .withProjectDir(projectDir.toFile())
    .apply(block)

fun BuildResult.assertNoDeprecationWarnings() = apply {
  assertThat(output)
    .doesNotContain(
      "has been deprecated and is scheduled to be removed in Gradle",
      "has been deprecated. This is scheduled to be removed in Gradle",
      "will fail with an error in Gradle",
    )
}

// TODO: https://youtrack.jetbrains.com/issue/KT-78620
fun String.toWarningsAsErrors(): Boolean = !contains("org.jetbrains.kotlin.multiplatform")
