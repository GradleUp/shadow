package com.github.jengelman.gradle.plugins.shadow

import assertk.Assert
import assertk.all
import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.SHADOW_INSTALL_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.SHADOW_RUN_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.internal.requireResourceAsPath
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.util.AppendableMavenRepository
import com.github.jengelman.gradle.plugins.shadow.util.JarBuilder
import com.github.jengelman.gradle.plugins.shadow.util.JarPath
import com.github.jengelman.gradle.plugins.shadow.util.JvmLang
import java.io.Closeable
import java.nio.file.Path
import java.util.Properties
import java.util.jar.JarEntry
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
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BasePluginTest {
  @TempDir
  lateinit var projectRoot: Path
    private set
  lateinit var localRepo: AppendableMavenRepository
    private set

  lateinit var artifactAJar: Path
    private set
  lateinit var artifactBJar: Path
    private set
  lateinit var entriesInA: Array<String>
    private set
  lateinit var entriesInB: Array<String>
    private set
  lateinit var entriesInAB: Array<String>
    private set

  val shadowJarPath = ":$SHADOW_JAR_TASK_NAME"
  val serverShadowJarPath = ":server:$SHADOW_JAR_TASK_NAME"
  val runShadowPath = ":$SHADOW_RUN_TASK_NAME"
  val installShadowDistPath = ":$SHADOW_INSTALL_TASK_NAME"
  val shadowDistZipPath = ":shadowDistZip"

  val projectScript: Path get() = path("build.gradle")
  val settingsScript: Path get() = path("settings.gradle")
  open val outputShadowedJar: JarPath get() = jarPath("build/libs/my-1.0-all.jar")
  val outputServerShadowedJar: JarPath get() = jarPath("server/build/libs/server-1.0-all.jar")

  @BeforeAll
  open fun doFirst() {
    localRepo = AppendableMavenRepository(
      createTempDirectory().resolve("local-maven-repo").createDirectories(),
      runner(projectDir = null),
    )
    localRepo.jarModule("junit", "junit", "3.8.2") {
      useJar(junitJar)
    }.jarModule("my", "a", "1.0") {
      buildJar {
        insert("a.properties", "a")
        insert("a2.properties", "a2")
      }
    }.jarModule("my", "b", "1.0") {
      buildJar {
        insert("b.properties", "b")
      }
    }.publish()

    artifactAJar = path("my/a/1.0/a-1.0.jar", parent = localRepo.root)
    artifactBJar = path("my/b/1.0/b-1.0.jar", parent = localRepo.root)
    entriesInA = arrayOf("a.properties", "a2.properties")
    entriesInB = arrayOf("b.properties")
    entriesInAB = entriesInA + entriesInB
  }

  @BeforeEach
  open fun setup() {
    projectScript.writeText(getDefaultProjectBuildScript(withGroup = true, withVersion = true))
    settingsScript.writeText(getDefaultSettingsBuildScript())
  }

  @AfterEach
  fun cleanup() {
    println(projectScript.readText())
  }

  @AfterAll
  fun doLast() {
    @OptIn(ExperimentalPathApi::class)
    localRepo.root.deleteRecursively()
  }

  fun getDefaultProjectBuildScript(
    plugin: String = "java",
    withGroup: Boolean = false,
    withVersion: Boolean = false,
  ): String {
    val groupInfo = if (withGroup) "group = 'my'" else ""
    val versionInfo = if (withVersion) "version = '1.0'" else ""
    return """
      plugins {
        id('$plugin')
        id('com.gradleup.shadow')
      }
      $groupInfo
      $versionInfo
    """.trimIndent() + lineSeparator
  }

  fun getDefaultSettingsBuildScript(
    startBlock: String = "",
    // Use a test-specific build cache directory. This ensures that we'll only use cached outputs generated during
    // this test, and we won't accidentally use cached outputs from a different test or a different build.
    // https://docs.gradle.org/current/userguide/build_cache.html#sec:build_cache_configure_local
    buildCacheBlock: String = "local { directory = file('build-cache') }",
    endBlock: String = "rootProject.name = 'my'",
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
      enableFeaturePreview 'TYPESAFE_PROJECT_ACCESSORS'
      $endBlock
    """.trimIndent() + lineSeparator
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

  fun buildJar(relative: String, builder: JarBuilder.() -> Unit): Path {
    return JarBuilder(path("temp/$relative")).apply(builder).write()
  }

  fun run(
    vararg arguments: String,
    runnerBlock: (GradleRunner) -> Unit = {},
  ): BuildResult {
    return runner(arguments = arguments.toList(), block = runnerBlock)
      .build().assertNoDeprecationWarnings()
  }

  fun runWithFailure(
    vararg arguments: String,
    runnerBlock: (GradleRunner) -> Unit = {},
  ): BuildResult {
    return runner(arguments = arguments.toList(), block = runnerBlock)
      .buildAndFail().assertNoDeprecationWarnings()
  }

  fun publishArtifactCD(circular: Boolean = false) {
    localRepo.jarModule("my", "c", "1.0") {
      buildJar {
        insert("c.properties", "c")
      }
      if (circular) {
        addDependency("my", "d", "1.0")
      }
    }.jarModule("my", "d", "1.0") {
      buildJar {
        insert("d.properties", "d")
      }
      addDependency("my", "c", "1.0")
    }.publish()
  }

  fun writeClass(
    sourceSet: String = "main",
    packageName: String = "my",
    withImports: Boolean = false,
    className: String = "Main",
    jvmLang: JvmLang = JvmLang.Java,
    content: () -> String = {
      when (jvmLang) {
        JvmLang.Groovy,
        JvmLang.Java,
        -> {
          val imports = if (withImports) "import junit.framework.Test;" else ""
          val classRef = if (withImports) "\"Refs: \" + Test.class.getName()" else "\"Refs: null\""
          """
            package $packageName;
            $imports
            public class $className {
              public static void main(String[] args) {
                if (args.length == 0) throw new IllegalArgumentException("No arguments provided.");
                String content = String.format("Hello, World! (%s) from $className", (Object[]) args);
                System.out.println(content);
                System.out.println($classRef);
              }
            }
          """.trimIndent()
        }
        JvmLang.Kotlin -> {
          val imports = if (withImports) "import junit.framework.Test" else ""
          val classRef = if (withImports) "\"Refs: \" + Test::class.java.name" else "\"Refs: null\""
          """
            @file:JvmName("$className")
            package $packageName
            $imports
            fun main(vararg args: String) {
              if (args.isEmpty()) throw IllegalArgumentException("No arguments provided.")
              val content ="Hello, World! (%s) from $className".format(*args)
              println(content)
              println($classRef)
            }
          """.trimIndent()
        }
        JvmLang.Scala -> {
          val imports = if (withImports) "import junit.framework.Test" else ""
          val classRef = if (withImports) "\"Refs: \" + classOf[Test].getName" else "\"Refs: null\""
          """
            package $packageName
            $imports
            object $className {
              def main(args: Array[String]): Unit = {
                if (args.isEmpty) throw new IllegalArgumentException("No arguments provided.")
                val content = s"Hello, World! (%s) from $className".format(args: _*)
                println(content)
                println($classRef)
              }
            }
          """.trimIndent()
        }
      }
    },
  ): String {
    val basePath = packageName.replace('.', '/') + "/$className"
    path("src/$sourceSet/$jvmLang/$basePath.${jvmLang.suffix}").writeText(content())
    return "$basePath.class"
  }

  fun writeClientAndServerModules(
    clientShadowed: Boolean = false,
    serverShadowBlock: String = "",
  ) {
    settingsScript.appendText(
      """
        include 'client', 'server'
      """.trimIndent(),
    )
    projectScript.writeText("")

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
      """.trimIndent() + lineSeparator,
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
        $shadowJarTask {
          $serverShadowBlock
        }
      """.trimIndent() + lineSeparator,
    )

    if (!clientShadowed) return
    path("client/build.gradle").appendText(
      """
        $shadowJarTask {
          relocate 'junit.framework', 'client.junit.framework'
        }
      """.trimIndent() + lineSeparator,
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

    projectScript.writeText(
      """
        ${getDefaultProjectBuildScript("java-gradle-plugin", withGroup = true, withVersion = true)}
        $gradlePluginBlock
      """.trimIndent() + lineSeparator,
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
    block: (GradleRunner) -> Unit = {},
  ): GradleRunner = GradleRunner.create()
    .withGradleVersion(testGradleVersion)
    .forwardOutput()
    .withPluginClasspath()
    .withTestKitDir(testKitDir.toFile())
    .withArguments(commonArguments + arguments)
    .apply {
      if (projectDir != null) {
        withProjectDir(projectDir.toFile())
      }
      block(this)
    }

  @Suppress("ConstPropertyName")
  companion object {
    private val testGradleVersion = System.getProperty("TEST_GRADLE_VERSION")
      ?: error("TEST_GRADLE_VERSION system property is not set.")

    val lineSeparator: String = System.lineSeparator()

    val testKitDir: Path = run {
      var gradleUserHome = System.getenv("GRADLE_USER_HOME")
      if (gradleUserHome == null) {
        gradleUserHome = Path(System.getProperty("user.home"), ".gradle").absolutePathString()
      }
      Path(gradleUserHome, "testkit")
    }

    val junitJar: Path = requireResourceAsPath("junit-3.8.2.jar")
    val junitRawEntries: List<JarEntry> = JarPath(junitJar)
      .use { it.entries().toList() }
      .filterNot {
        // This entry is not present in the jar file.
        it.name == "junit3.8.2/"
      }
    val junitEntries: Array<String> = junitRawEntries.map { it.name }.toTypedArray()
    const val manifestEntry = "META-INF/MANIFEST.MF"
    val manifestEntries = arrayOf("META-INF/", manifestEntry)

    val shadowJarTask: String = "tasks.named('$SHADOW_JAR_TASK_NAME', ${ShadowJar::class.java.name})"
    const val runShadowTask = "tasks.named('$SHADOW_RUN_TASK_NAME', JavaExec)"
    const val jarTask = "tasks.named('jar', Jar)"

    val commonArguments = listOf(
      "--warning-mode=fail",
      "--configuration-cache",
      "--build-cache",
      "--parallel",
      "--stacktrace",
      // https://docs.gradle.org/current/userguide/configuration_cache.html#config_cache:usage:parallel
      "-Dorg.gradle.configuration-cache.parallel=true",
    )

    const val INFO_ARGUMENT = "--info"

    // TODO: enable this flag for all tests once we have fixed all issues with isolated projects.
    //  See https://github.com/GradleUp/shadow/pull/1139.
    const val IP_ARGUMENT = "-Dorg.gradle.unsafe.isolated-projects=true"

    fun String.toProperties(): Properties = Properties().apply { load(byteInputStream()) }

    fun implementationFiles(vararg paths: Path): String {
      return paths.joinToString(lineSeparator) { "implementation files('${it.invariantSeparatorsPathString}')" }
    }

    inline fun <reified T : ResourceTransformer> transform(
      dependenciesBlock: String = "",
      transformerBlock: String = "",
    ): String {
      return """
        dependencies {
          $dependenciesBlock
        }
        $shadowJarTask {
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
