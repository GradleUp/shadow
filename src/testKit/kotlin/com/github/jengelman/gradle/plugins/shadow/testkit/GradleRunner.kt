package com.github.jengelman.gradle.plugins.shadow.testkit

import assertk.assertThat
import assertk.assertions.doesNotContain
import com.github.jengelman.gradle.plugins.shadow.TestKitBuildConfig.TEST_GRADLE_VERSION
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion

private val testKitDir by lazy {
  val gradleUserHome =
    System.getenv("GRADLE_USER_HOME")
      ?: Path(System.getProperty("user.home"), ".gradle").absolutePathString()
  Path(gradleUserHome, "testkit")
}

// TODO: this could be inlined after bumping the min Gradle requirement to 9.6 or above.
val enableNoImplicitLookupInParentProjects: String
  get() =
    when {
      GradleVersion.version(TEST_GRADLE_VERSION) >= GradleVersion.version("9.6.0") ->
        "enableFeaturePreview 'NO_IMPLICIT_LOOKUP_IN_PARENT_PROJECTS'"
      else -> ""
    }

// TODO: this could be inlined after bumping the min Gradle requirement to 9.7 or above.
private val isolatedProjectsFlag: String
  get() =
    when {
      GradleVersion.version(TEST_GRADLE_VERSION) >= GradleVersion.version("9.7.0-rc-1") ->
        "--isolated-projects"
      else -> "-Dorg.gradle.unsafe.isolated-projects=true"
    }

val commonGradleArgs =
  setOf(
    "--configuration-cache",
    "--build-cache",
    "--stacktrace",
    "--warning-mode=fail",
    "-Dorg.gradle.kotlin.dsl.allWarningsAsErrors=true",
    "-Dorg.gradle.tooling.parallel=true",
    // https://docs.gradle.org/current/userguide/configuration_cache.html#config_cache:usage:parallel
    "-Dorg.gradle.configuration-cache.parallel=true",
    // https://docs.gradle.org/current/userguide/isolated_projects.html#how_do_i_use_it
    isolatedProjectsFlag,
  )

fun gradleRunner(
  projectDir: Path,
  arguments: Iterable<String>,
  block: GradleRunner.() -> Unit = {},
): GradleRunner =
  GradleRunner.create()
    .withGradleVersion(TEST_GRADLE_VERSION)
    .forwardOutput()
    .withPluginClasspath()
    .withTestKitDir(testKitDir.toFile())
    .withArguments(arguments.toList())
    .withProjectDir(projectDir.toFile())
    .apply(block)

fun BuildResult.assertNoDeprecationWarnings() = apply {
  assertThat(output)
    .doesNotContain(
      "has been deprecated",
      "will fail with an error in Gradle",
    )
}
