package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.util.AppendableJar
import com.github.jengelman.gradle.plugins.shadow.util.AppendableMavenFileRepository
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.appendText
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.toPath
import kotlin.io.path.writeText
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BasePluginTest {
  lateinit var root: Path

  lateinit var repo: AppendableMavenFileRepository

  @BeforeEach
  fun setup() {
    root = createTempDirectory()

    repo = repo()
    repo.module("junit", "junit", "3.8.2")
      .use(testJar)
      .publish()

    buildScript.writeText(getProjectBuildScript(withGroup = true, withVersion = true))
    buildScript.appendText(System.lineSeparator())
    settingsScript.writeText(getSettingsBuildScript())
    settingsScript.appendText(System.lineSeparator())
  }

  @ExperimentalPathApi
  @AfterEach
  fun cleanup() {
    runCatching {
      // TODO: workaround for https://github.com/junit-team/junit5/issues/2811.
      root.deleteRecursively()
    }

    println(buildScript.readText())
  }

  @Language("Groovy")
  protected fun getProjectBuildScript(
    javaPlugin: String = "java",
    withGroup: Boolean = false,
    withVersion: Boolean = false,
  ): String {
    val groupInfo = if (withGroup) "group = 'shadow'" else ""
    val versionInfo = if (withVersion) "version = '1.0'" else ""

    return """
      plugins {
          id('$javaPlugin')
          id('com.gradleup.shadow')
      }
      $groupInfo
      $versionInfo
    """.trimIndent()
  }

  @Language("Groovy")
  protected fun getSettingsBuildScript(
    start: () -> String = { "" },
    end: () -> String = { "rootProject.name = 'shadow'" },
  ): String {
    return """
      ${start()}
      dependencyResolutionManagement {
        repositories {
          maven { url = '${repo.uri}' }
          mavenCentral()
        }
      }
      ${end()}
    """.trimIndent()
  }

  protected val buildScript: Path
    get() = path("build.gradle")

  protected val settingsScript: Path
    get() = path("settings.gradle")

  protected fun buildJar(path: String): AppendableJar {
    return AppendableJar(path(path))
  }

  protected val outputShadowJar: Path
    get() = path("build/libs/shadow-1.0-all.jar")

  protected fun output(name: String): Path {
    return path("build/libs/$name")
  }

  protected fun path(path: String): Path {
    return root.resolve(path).also {
      if (!it.exists()) {
        it.parent.createDirectories()
        it.createFile()
      }
    }
  }

  protected fun repo(path: String = "maven-repo"): AppendableMavenFileRepository {
    return AppendableMavenFileRepository(root.resolve(path))
  }

  protected fun assertContains(jarPath: Path, paths: List<String>) {
    JarFile(jarPath.toFile()).use { jar ->
      paths.forEach { path ->
        assert(jar.getJarEntry(path) != null) { "Jar file $jarPath does not contain entry $path" }
      }
    }
  }

  protected fun assertDoesNotContain(jarPath: Path, paths: List<String>) {
    JarFile(jarPath.toFile()).use { jar ->
      paths.forEach { path ->
        assert(jar.getJarEntry(path) == null) { "Jar file $jarPath contains entry $path" }
      }
    }
  }

  protected val runner: GradleRunner
    get() {
      return GradleRunner.create()
        .withProjectDir(root.toFile())
        .forwardOutput()
        .withPluginClasspath()
        .withTestKitDir(testKitDir.toFile())
    }

  protected fun runner(arguments: Iterable<String>): GradleRunner {
    val allArguments = listOf(
      "--warning-mode=fail",
      "--configuration-cache",
      "--stacktrace",
    ) + arguments
    return runner.withArguments(allArguments)
  }

  protected fun run(vararg tasks: String): BuildResult {
    return run(tasks.toList())
  }

  protected inline fun run(
    tasks: Iterable<String>,
    block: (GradleRunner) -> GradleRunner = { it },
  ): BuildResult {
    return block(runner(tasks)).build().also {
      it.assertNoDeprecationWarnings()
    }
  }

  protected fun runWithDebug(vararg tasks: String): BuildResult {
    return run(tasks.toList()) { it.withDebug(true) }
  }

  protected fun runWithFailure(
    vararg tasks: String,
    block: (GradleRunner) -> GradleRunner = { it },
  ): BuildResult {
    return block(runner(tasks.toList())).buildAndFail().also {
      it.assertNoDeprecationWarnings()
    }
  }

  protected companion object {
    val testKitDir: Path = run {
      var gradleUserHome = System.getenv("GRADLE_USER_HOME")
      if (gradleUserHome == null) {
        gradleUserHome = Path(System.getProperty("user.home"), ".gradle").absolutePathString()
      }
      Path(gradleUserHome, "testkit")
    }

    val testJar: Path = requireNotNull(this::class.java.classLoader.getResource("junit-3.8.2.jar")).toURI().toPath()

    val shadowJar: String = """
      tasks.named("shadowJar", ${ShadowJar::class.java.name})
    """.trimIndent()

    val runShadow = """
      tasks.named('runShadow')
    """.trimIndent()

    fun BuildResult.assertNoDeprecationWarnings() {
      output.lines().forEach {
        assert(!containsDeprecationWarning(it))
      }
    }

    fun containsDeprecationWarning(output: String): Boolean {
      return output.contains("has been deprecated and is scheduled to be removed in Gradle") ||
        output.contains("has been deprecated. This is scheduled to be removed in Gradle")
    }

    fun escapedPath(path: Path): String {
      return path.toString().replace("\\", "\\\\")
    }
  }
}
