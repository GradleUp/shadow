package com.github.jengelman.gradle.plugins.shadow

import assertk.Assert
import assertk.all
import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.SHADOW_RUN_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.internal.requireResourceAsPath
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.util.AppendableMavenRepository
import com.github.jengelman.gradle.plugins.shadow.util.JarPath
import java.io.Closeable
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.appendText
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BasePluginTest {
  @TempDir
  lateinit var projectRoot: Path
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
    projectScriptPath.writeText(getDefaultProjectBuildScript(withGroup = true, withVersion = true))
    settingsScriptPath.writeText(getDefaultSettingsBuildScript())
  }

  @AfterEach
  fun cleanup() {
    println(projectScriptPath.readText())
  }

  @OptIn(ExperimentalPathApi::class)
  fun doLast() {
    localRepo.root.deleteRecursively()
  }

  val shadowJarTask = ":$SHADOW_JAR_TASK_NAME"
  val serverShadowJarTask = ":server:$SHADOW_JAR_TASK_NAME"
  val runShadowTask = ":$SHADOW_RUN_TASK_NAME"

  val projectScriptPath: Path get() = path("build.gradle")
  val settingsScriptPath: Path get() = path("settings.gradle")
  open val outputShadowJar: JarPath get() = jarPath("build/libs/shadow-1.0-all.jar")
  val outputServerShadowJar: JarPath get() = jarPath("server/build/libs/server-1.0-all.jar")

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
    // Use a test-specific build cache directory. This ensures that we'll only use cached outputs generated during
    // this test, and we won't accidentally use cached outputs from a different test or a different build.
    // https://docs.gradle.org/current/userguide/build_cache.html#sec:build_cache_configure_local
    buildCacheBlock: String = "local { directory = file('build-cache') }",
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
      buildCache {
        $buildCacheBlock
      }
      $endBlock
    """.trimIndent() + System.lineSeparator()
  }

  fun jarPath(relative: String, parent: Path = projectRoot): JarPath {
    return JarPath(parent.resolve(relative))
  }

  fun path(relative: String, parent: Path = projectRoot): Path {
    return parent.resolve(relative).also {
      if (it.exists()) return@also
      it.parent.createDirectories()
      if (relative.endsWith("/")) {
        it.createDirectory()
      } else {
        // We should create text file only if it doesn't exist.
        it.createFile()
      }
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

  fun publishArtifactCD(circular: Boolean = false) {
    localRepo.module("shadow", "c", "1.0") {
      buildJar {
        insert("c.properties", "c")
      }
      if (circular) {
        addDependency("shadow", "d", "1.0")
      }
    }.module("shadow", "d", "1.0") {
      buildJar {
        insert("d.properties", "d")
      }
      addDependency("shadow", "c", "1.0")
    }.publish()
  }

  fun writeMainClass(
    sourceSet: String = "main",
    withImports: Boolean = false,
    className: String = "Main",
  ) {
    val imports = if (withImports) "import junit.framework.Test;" else ""
    val classRef = if (withImports) "\"Refs: \" + Test.class.getName()" else "\"Refs: null\""
    path("src/$sourceSet/java/shadow/$className.java").writeText(
      """
        package shadow;
        $imports
        public class $className {
          public static void main(String[] args) {
            if (args.length == 0) {
              throw new IllegalArgumentException("No arguments provided.");
            }
            String content = String.format("Hello, World! (%s) from $className", (Object[]) args);
            System.out.println(content);
            System.out.println($classRef);
          }
        }
      """.trimIndent(),
    )
  }

  fun writeClientAndServerModules(
    clientShadowed: Boolean = false,
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

    if (!clientShadowed) return
    path("client/build.gradle").appendText(
      """
        $shadowJar {
          relocate 'junit.framework', 'client.junit.framework'
        }
      """.trimIndent() + System.lineSeparator(),
    )
    path("server/src/main/java/server/Server.java").writeText(
      """
        package server;
        import client.Client;
        import client.junit.framework.Test;
        public class Server {}
      """.trimIndent(),
    )
    val replaced = path("server/build.gradle").readText()
      .replace("project(':client')", "project(path: ':client', configuration: 'shadow')")
    path("server/build.gradle").writeText(replaced)
  }

  fun writeGradlePluginModule(legacy: Boolean) {
    val pluginId = "my.plugin"
    val pluginClass = "my.plugin.MyPlugin"
    val gradlePluginBlock: String

    if (legacy) {
      gradlePluginBlock = ""
      path("src/main/resources/META-INF/gradle-plugins/$pluginId.properties")
        .writeText("implementation-class=$pluginClass")
    } else {
      gradlePluginBlock = """
        gradlePlugin {
          plugins {
            create('myPlugin') {
              id = '$pluginId'
              implementationClass = '$pluginClass'
            }
          }
        }
      """.trimIndent()
    }

    projectScriptPath.writeText(
      """
        ${getDefaultProjectBuildScript("java-gradle-plugin", withGroup = true, withVersion = true)}
        $gradlePluginBlock
      """.trimIndent() + System.lineSeparator(),
    )

    path("src/main/java/my/plugin/MyPlugin.java").writeText(
      """
        package my.plugin;
        import org.gradle.api.Plugin;
        import org.gradle.api.Project;
        public class MyPlugin implements Plugin<Project> {
          public void apply(Project project) {
            System.out.println("MyPlugin: Hello, World!");
          }
        }
      """.trimIndent(),
    )
  }

  fun runner(
    arguments: Iterable<String> = emptyList(),
    projectDir: Path? = projectRoot,
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

    val testJar: Path = requireResourceAsPath("junit-3.8.2.jar")
    val artifactJar: Path = requireResourceAsPath("test-artifact-1.0-SNAPSHOT.jar")
    val projectJar: Path = requireResourceAsPath("test-project-1.0-SNAPSHOT.jar")

    val shadowJar: String = """
      tasks.named('$SHADOW_JAR_TASK_NAME', ${ShadowJar::class.java.name})
    """.trimIndent()

    val runShadow = "tasks.named('$SHADOW_RUN_TASK_NAME', JavaExec)".trim()

    val commonArguments = listOf(
      "--warning-mode=fail",
      "--configuration-cache",
      "--build-cache",
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
      assertThat(output).doesNotContain(
        "has been deprecated and is scheduled to be removed in Gradle",
        "has been deprecated. This is scheduled to be removed in Gradle",
      )
    }

    fun <T : Closeable> Assert<T>.useAll(body: Assert<T>.() -> Unit) = all {
      body()
      // Close the resource after all assertions are done.
      given { it.use(block = {}) }
    }

    fun Assert<BuildResult>.taskOutcomeEquals(taskPath: String, expectedOutcome: TaskOutcome) {
      return transform { it.task(taskPath)?.outcome }.isNotNull().isEqualTo(expectedOutcome)
    }
  }
}
