package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.SHADOW_RUN_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.tasks.JavaJarExec
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
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.io.path.toPath
import kotlin.io.path.writeText
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BasePluginTest {
  private lateinit var root: Path
  lateinit var repo: AppendableMavenFileRepository

  @BeforeEach
  open fun setup() {
    root = createTempDirectory()

    repo = repo()
    repo.module("junit", "junit", "3.8.2")
      .use(testJar)
      .publish()

    projectScriptPath.writeText(getDefaultProjectBuildScript(withGroup = true, withVersion = true))
    settingsScriptPath.writeText(getDefaultSettingsBuildScript())
  }

  @ExperimentalPathApi
  @AfterEach
  fun cleanup() {
    runCatching {
      // TODO: workaround for https://github.com/junit-team/junit5/issues/2811.
      root.deleteRecursively()
    }

    println(projectScriptPath.readText())
  }

  fun getDefaultProjectBuildScript(
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
    """.trimIndent() + System.lineSeparator()
  }

  fun getDefaultSettingsBuildScript(
    startBlock: String = "",
    endBlock: String = "rootProject.name = 'shadow'",
  ): String {
    return """
      $startBlock
      dependencyResolutionManagement {
        repositories {
          maven { url = '${repo.uri}' }
          mavenCentral()
        }
      }
      $endBlock
    """.trimIndent() + System.lineSeparator()
  }

  fun publishArtifactA() {
    repo.module("shadow", "a", "1.0")
      .insertFile("a.properties", "a")
      .insertFile("a2.properties", "a2")
      .publish()
  }

  fun publishArtifactB() {
    repo.module("shadow", "b", "1.0")
      .insertFile("b.properties", "b")
      .publish()
  }

  fun publishArtifactCD(circular: Boolean = false) {
    repo.module("shadow", "c", "1.0")
      .insertFile("c.properties", "c")
      .apply { if (circular) dependsOn("d") }
      .publish()
    repo.module("shadow", "d", "1.0")
      .insertFile("d.properties", "d")
      .dependsOn("c")
      .publish()
  }

  fun buildJar(path: String): AppendableJar {
    return AppendableJar(path(path))
  }

  open val shadowJarTask = SHADOW_JAR_TASK_NAME
  open val runShadowTask = SHADOW_RUN_TASK_NAME

  val projectScriptPath: Path
    get() = path("build.gradle")

  val settingsScriptPath: Path
    get() = path("settings.gradle")

  val outputShadowJar: Path
    get() = path("build/libs/shadow-1.0-all.jar")

  fun path(path: String): Path {
    return root.resolve(path).also {
      val extension = it.extension
      // Binary files should not be created, text files should be created.
      if (it.exists() || extension == "jar" || extension == "zip") return@also

      it.parent.createDirectories()
      it.createFile()
    }
  }

  fun repo(path: String = "maven-repo"): AppendableMavenFileRepository {
    return AppendableMavenFileRepository(root.resolve(path))
  }

  fun assertContains(jarPath: Path, paths: List<String>) {
    JarFile(jarPath.toFile()).use { jar ->
      paths.forEach { path ->
        assert(jar.getJarEntry(path) != null) { "Jar file $jarPath does not contain entry $path" }
      }
    }
  }

  fun assertDoesNotContain(jarPath: Path, paths: List<String>) {
    JarFile(jarPath.toFile()).use { jar ->
      paths.forEach { path ->
        assert(jar.getJarEntry(path) == null) { "Jar file $jarPath contains entry $path" }
      }
    }
  }

  private val runner: GradleRunner
    get() {
      return GradleRunner.create()
        .withProjectDir(root.toFile())
        .forwardOutput()
        .withPluginClasspath()
        .withTestKitDir(testKitDir.toFile())
    }

  fun runner(arguments: Iterable<String>): GradleRunner {
    val allArguments = listOf(
      "--warning-mode=fail",
      "--configuration-cache",
      "--stacktrace",
    ) + arguments
    return runner.withArguments(allArguments)
  }

  fun run(vararg tasks: String): BuildResult {
    return run(tasks.toList())
  }

  fun run(
    vararg tasks: String,
    block: (GradleRunner) -> GradleRunner = { it },
  ): BuildResult {
    return run(tasks.toList(), block)
  }

  inline fun run(
    tasks: Iterable<String>,
    block: (GradleRunner) -> GradleRunner = { it },
  ): BuildResult {
    return block(runner(tasks)).build().also {
      it.assertNoDeprecationWarnings()
    }
  }

  fun runWithFailure(
    vararg tasks: String,
    block: (GradleRunner) -> GradleRunner = { it },
  ): BuildResult {
    return block(runner(tasks.toList())).buildAndFail().also {
      it.assertNoDeprecationWarnings()
    }
  }

  fun writeClientAndServerModules(
    serverShadowBlock: String = "",
  ) {
    settingsScriptPath.appendText(
      """
        include 'client', 'server'
      """.trimIndent(),
    )
    projectScriptPath.writeText("")

    path("client/src/main/java/client/Client.java").writeText(
      """
        package client;
        public class Client {}
      """.trimIndent(),
    )
    path("client/build.gradle").writeText(
      """
        ${getDefaultProjectBuildScript("java", withVersion = true)}
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
      """.trimIndent() + System.lineSeparator(),
    )

    path("server/src/main/java/server/Server.java").writeText(
      """
        package server;
        import client.Client;
        public class Server {}
      """.trimIndent(),
    )
    path("server/build.gradle").writeText(
      """
        ${getDefaultProjectBuildScript("java", withVersion = true)}
        dependencies {
          implementation project(':client')
        }
        $shadowJar {
          $serverShadowBlock
        }
      """.trimIndent() + System.lineSeparator(),
    )
  }

  fun writeApiLibAndImplModules() {
    settingsScriptPath.appendText(
      """
        include 'api', 'lib', 'impl'
      """.trimIndent() + System.lineSeparator(),
    )
    projectScriptPath.writeText("")

    path("lib/src/main/java/lib/LibEntity.java").writeText(
      """
        package lib;
        public interface LibEntity {}
      """.trimIndent(),
    )
    path("lib/src/main/java/lib/UnusedLibEntity.java").writeText(
      """
        package lib;
        public class UnusedLibEntity implements LibEntity {}
      """.trimIndent(),
    )
    path("lib/build.gradle").writeText(
      """
        plugins {
          id 'java'
        }
      """.trimIndent() + System.lineSeparator(),
    )

    path("api/src/main/java/api/Entity.java").writeText(
      """
        package api;
        public interface Entity {}
      """.trimIndent(),
    )
    path("api/src/main/java/api/UnusedEntity.java").writeText(
      """
        package api;
        import lib.LibEntity;
        public class UnusedEntity implements LibEntity {}
      """.trimIndent(),
    )
    path("api/build.gradle").writeText(
      """
        plugins {
          id 'java'
        }
        dependencies {
          implementation 'junit:junit:3.8.2'
          implementation project(':lib')
        }
      """.trimIndent() + System.lineSeparator(),
    )

    path("impl/src/main/java/impl/SimpleEntity.java").writeText(
      """
        package impl;
        import api.Entity;
        public class SimpleEntity implements Entity {}
      """.trimIndent(),
    )
    path("impl/build.gradle").writeText(
      """
        ${getDefaultProjectBuildScript("java-library")}
        dependencies {
          api project(':api')
        }
        $shadowJar {
          minimize()
        }
      """.trimIndent() + System.lineSeparator(),
    )
  }

  companion object {
    val javaVersion: Int = JavaVersion.current().majorVersion.toInt()

    val testKitDir: Path = run {
      var gradleUserHome = System.getenv("GRADLE_USER_HOME")
      if (gradleUserHome == null) {
        gradleUserHome = Path(System.getProperty("user.home"), ".gradle").absolutePathString()
      }
      Path(gradleUserHome, "testkit")
    }

    val testJar: Path = requireNotNull(this::class.java.classLoader.getResource("junit-3.8.2.jar")).toURI().toPath()

    val shadowJar: String = """
      tasks.named('$SHADOW_JAR_TASK_NAME', ${ShadowJar::class.java.name})
    """.trimIndent()

    val runShadow = """
      tasks.named('$SHADOW_RUN_TASK_NAME', ${JavaJarExec::class.java.name})
    """.trimIndent()

    fun BuildResult.assertNoDeprecationWarnings() {
      output.lines().forEach {
        assert(!containsDeprecationWarning(it))
      }
    }

    private fun containsDeprecationWarning(output: String): Boolean {
      return output.contains("has been deprecated and is scheduled to be removed in Gradle") ||
        output.contains("has been deprecated. This is scheduled to be removed in Gradle")
    }
  }
}
