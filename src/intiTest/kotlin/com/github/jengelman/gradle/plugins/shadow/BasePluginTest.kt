package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.SHADOW_RUN_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.tasks.JavaJarExec
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.util.AppendableMavenRepository
import com.github.jengelman.gradle.plugins.shadow.util.JarPath
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.appendText
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.toPath
import kotlin.io.path.writeText
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BasePluginTest {
  lateinit var root: Path
  lateinit var localRepo: AppendableMavenRepository

  @BeforeAll
  open fun doFirst() {
    localRepo = AppendableMavenRepository(
      createTempDirectory().resolve("local-maven-repo"),
      runner(projectDir = null),
    )
    localRepo.module("junit", "junit", "3.8.2") {
      useJar(testJar)
    }.module("shadow", "a", "1.0") {
      buildJar {
        insert("a.properties", "a")
        insert("a2.properties", "a2")
      }
    }.module("shadow", "b", "1.0") {
      buildJar {
        insert("b.properties", "b")
      }
    }.publish()
  }

  @BeforeEach
  open fun setup() {
    root = createTempDirectory()

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

  @OptIn(ExperimentalPathApi::class)
  fun doLast() {
    localRepo.root.deleteRecursively()
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
          maven { url = '${localRepo.root.toUri()}' }
          mavenCentral()
        }
      }
      $endBlock
    """.trimIndent() + System.lineSeparator()
  }

  open val shadowJarTask = ":$SHADOW_JAR_TASK_NAME"
  open val runShadowTask = ":$SHADOW_RUN_TASK_NAME"
  val serverShadowJarTask = ":server:$SHADOW_JAR_TASK_NAME"

  val projectScriptPath: Path
    get() = path("build.gradle")

  val settingsScriptPath: Path
    get() = path("settings.gradle")

  open val outputShadowJar: JarPath
    get() = jarPath("build/libs/shadow-1.0-all.jar")

  val outputServerShadowJar: JarPath
    get() = jarPath("server/build/libs/server-1.0-all.jar")

  fun jarPath(path: String): JarPath {
    val realPath = root.resolve(path).also {
      check(it.exists()) { "Path not found: $it" }
      check(it.isRegularFile()) { "Path is not a regular file: $it" }
    }
    return JarPath(realPath)
  }

  fun path(path: String): Path {
    return root.resolve(path).also {
      if (it.exists()) return@also
      it.parent.createDirectories()
      // We should create text file only if it doesn't exist.
      it.createFile()
    }
  }

  inline fun run(
    vararg tasks: String,
    runnerBlock: (GradleRunner) -> GradleRunner = { it },
  ): BuildResult {
    return runnerBlock(runner(tasks.toList())).build().assertNoDeprecationWarnings()
  }

  inline fun runWithFailure(
    vararg tasks: String,
    runnerBlock: (GradleRunner) -> GradleRunner = { it },
  ): BuildResult {
    return runnerBlock(runner(tasks.toList())).buildAndFail().assertNoDeprecationWarnings()
  }

  fun writeMainClass(
    withImports: Boolean = false,
  ) {
    val imports = if (withImports) "import junit.framework.Test;" else ""

    path("src/main/java/shadow/Main.java").writeText(
      """
        package shadow;
        $imports
        public class Main {
          public static void main(String[] args) {
            System.out.println("Hello, World!");
          }
        }
      """.trimIndent(),
    )
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
        ${getDefaultProjectBuildScript("java")}
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

  fun runner(
    arguments: Iterable<String> = emptyList(),
    projectDir: Path? = root,
  ): GradleRunner = GradleRunner.create()
    .forwardOutput()
    .withPluginClasspath()
    .withTestKitDir(testKitDir.toFile())
    .withArguments(commonArguments + arguments)
    .apply {
      if (projectDir != null) {
        withProjectDir(projectDir.toFile())
      }
    }

  companion object {
    val testKitDir: Path = run {
      var gradleUserHome = System.getenv("GRADLE_USER_HOME")
      if (gradleUserHome == null) {
        gradleUserHome = Path(System.getProperty("user.home"), ".gradle").absolutePathString()
      }
      Path(gradleUserHome, "testkit")
    }

    val testJar: Path = requireNotNull(this::class.java.classLoader.getResource("junit-3.8.2.jar")).toURI().toPath()
    val artifactJar: Path =
      requireNotNull(this::class.java.classLoader.getResource("test-artifact-1.0-SNAPSHOT.jar")).toURI().toPath()
    val projectJar: Path =
      requireNotNull(this::class.java.classLoader.getResource("test-project-1.0-SNAPSHOT.jar")).toURI().toPath()

    val shadowJar: String = """
      tasks.named('$SHADOW_JAR_TASK_NAME', ${ShadowJar::class.java.name})
    """.trimIndent()

    val runShadow = """
      tasks.named('$SHADOW_RUN_TASK_NAME', ${JavaJarExec::class.java.name})
    """.trimIndent()

    val commonArguments = listOf(
      "--warning-mode=fail",
      "--configuration-cache",
      "--stacktrace",
    )

    fun String.toProperties(): Properties = Properties().apply { load(byteInputStream()) }

    fun fromJar(vararg paths: Path): String {
      return paths.joinToString(System.lineSeparator()) { "from('${it.toUri().toURL().path}')" }
    }

    inline fun <reified T : Transformer> transform(
      shadowJarBlock: String = "",
      transformerBlock: String = "",
    ): String {
      return """
      $shadowJar {
        $shadowJarBlock
        transform(${T::class.java.name}) {
          $transformerBlock
        }
      }
      """.trimIndent()
    }

    fun BuildResult.assertNoDeprecationWarnings() = apply {
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
