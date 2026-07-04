package com.github.jengelman.gradle.plugins.shadow.testkit

import assertk.assertThat
import assertk.assertions.doesNotContain
import com.github.jengelman.gradle.plugins.shadow.TestKitBuildConfig
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

const val testGradleVersion: String = TestKitBuildConfig.TEST_GRADLE_VERSION

// TODO: this could be inlined after bumping the min Gradle requirement to 9.6 or above.
val enableNoImplicitLookupInParentProjects: String
  get() =
    when {
      GradleVersion.version(testGradleVersion) >= GradleVersion.version("9.6.0") ->
        "enableFeaturePreview 'NO_IMPLICIT_LOOKUP_IN_PARENT_PROJECTS'"
      else -> ""
    }

val commonGradleArgs =
  setOf(
    "--configuration-cache",
    "--build-cache",
    "--stacktrace",
    // https://docs.gradle.org/current/userguide/configuration_cache.html#config_cache:usage:parallel
    "-Dorg.gradle.configuration-cache.parallel=true",
    // https://docs.gradle.org/current/userguide/isolated_projects.html#how_do_i_use_it
    "-Dorg.gradle.unsafe.isolated-projects=true",
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
