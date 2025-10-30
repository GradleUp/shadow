package com.github.jengelman.gradle.plugins.shadow

import assertk.Assert
import assertk.all
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.SHADOW_INSTALL_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin.Companion.SHADOW_RUN_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.assertNoDeprecationWarnings
import com.github.jengelman.gradle.plugins.shadow.testkit.commonGradleArgs
import com.github.jengelman.gradle.plugins.shadow.testkit.gradleRunner
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsPath
import com.github.jengelman.gradle.plugins.shadow.testkit.toWarningsAsErrors
import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.util.AppendableMavenRepository
import com.github.jengelman.gradle.plugins.shadow.util.JarBuilder
import com.github.jengelman.gradle.plugins.shadow.util.JvmLang
import java.io.Closeable
import java.nio.file.Path
import java.util.Properties
import java.util.jar.JarEntry
import kotlin.io.path.ExperimentalPathApi
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

  val projectScript: Path get() = path("build.gradle")
  val settingsScript: Path get() = path("settings.gradle")
  val outputJar: JarPath get() = jarPath("build/libs/my-1.0.jar")
  open val outputShadowedJar: JarPath get() = jarPath("build/libs/my-1.0-all.jar")
  val outputServerShadowedJar: JarPath get() = jarPath("server/build/libs/server-1.0-all.jar")

  @BeforeAll
  fun beforeAll() {
    localRepo = AppendableMavenRepository(
      root = createTempDirectory().resolve("local-maven-repo").createDirectories(),
    ).apply {
      jarModule("junit", "junit", "3.8.2") {
        useJar(junitJar)
      }
      val a = jarModule("my", "a", "1.0") {
        buildJar {
          insert("a.properties", "a")
          insert("a2.properties", "a2")
        }
      }
      val b = jarModule("my", "b", "1.0") {
        buildJar {
          insert("b.properties", "b")
        }
      }
      val c = jarModule("my", "c", "1.0") {
        buildJar {
          insert("c.properties", "c")
        }
      }
      val d = jarModule("my", "d", "1.0") {
        buildJar {
          insert("d.properties", "d")
        }
        // Depends on c but c does not depend on d.
        addDependency(c)
      }
      val e = jarModule("my", "e", "1.0") {
        buildJar {
          insert("e.properties", "e")
        }
        // Circular dependency with f.
        addDependency("my:f:1.0")
      }
      val f = jarModule("my", "f", "1.0") {
        buildJar {
          insert("f.properties", "f")
        }
        // Circular dependency with e.
        addDependency(e)
      }
      bomModule("my", "bom", "1.0") {
        addDependency(a)
        addDependency(b)
        addDependency(c)
        addDependency(d)
        addDependency(e)
        addDependency(f)
      }
    }
    localRepo.publish()

    artifactAJar = path("my/a/1.0/a-1.0.jar", parent = localRepo.root)
    artifactBJar = path("my/b/1.0/b-1.0.jar", parent = localRepo.root)
  }

  @BeforeEach
  open fun beforeEach() {
    projectScript.writeText(getDefaultProjectBuildScript())
    settingsScript.writeText(getDefaultSettingsBuildScript())
  }

  @AfterEach
  fun afterEach() {
    println(projectScript.readText())
  }

  @AfterAll
  fun afterAll() {
    @OptIn(ExperimentalPathApi::class)
    localRepo.root.deleteRecursively()
  }

  fun getDefaultProjectBuildScript(
    plugin: String = "java",
    withGroup: Boolean = true,
    withVersion: Boolean = true,
    applyShadowPlugin: Boolean = true,
  ): String {
    val groupInfo = if (withGroup) "group = 'my'" else ""
    val versionInfo = if (withVersion) "version = '1.0'" else ""
    return """
      plugins {
        id '$plugin'
        id '$shadowPluginId' apply $applyShadowPlugin
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

  fun runWithSuccess(
    vararg arguments: String,
    block: GradleRunner.() -> Unit = {},
  ): BuildResult {
    return runner(arguments = arguments.toList(), block = block)
      .build().assertNoDeprecationWarnings()
  }

  fun runWithFailure(
    vararg arguments: String,
    block: GradleRunner.() -> Unit = {},
  ): BuildResult {
    return runner(arguments = arguments.toList(), block = block)
      .buildAndFail().assertNoDeprecationWarnings()
  }

  fun writeClass(
    sourceSet: String = "main",
    packageName: String = "my",
    withImports: Boolean = false,
    className: String = "Main",
    jvmLang: JvmLang = JvmLang.Java,
    content: () -> String = {
      when (jvmLang) {
        JvmLang.Java -> {
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
        ${getDefaultProjectBuildScript("java")}
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
        ${getDefaultProjectBuildScript("java")}
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

  fun writeGradlePluginModule() {
    projectScript.writeText(
      """
        ${getDefaultProjectBuildScript("java-gradle-plugin")}
        gradlePlugin {
          plugins {
            create('myPlugin') {
              id = 'my.plugin'
              implementationClass = 'my.plugin.MyPlugin'
            }
          }
        }
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

  private fun runner(
    arguments: Iterable<String>,
    block: GradleRunner.() -> Unit,
  ): GradleRunner {
    val warningsAsErrors = try {
      projectScript.readText().toWarningsAsErrors()
    } catch (_: UninitializedPropertyAccessException) {
      true // Default warning mode if projectScript is not initialized yet.
    }
    return gradleRunner(
      projectDir = projectRoot,
      arguments = commonGradleArgs + arguments,
      warningsAsErrors = warningsAsErrors,
      block = block,
    )
  }

  @Suppress("ConstPropertyName")
  companion object {
    val lineSeparator: String = System.lineSeparator()

    const val shadowPluginId = "com.gradleup.shadow"
    const val shadowJarPath = ":$SHADOW_JAR_TASK_NAME"
    const val serverShadowJarPath = ":server:$SHADOW_JAR_TASK_NAME"
    const val runShadowPath = ":$SHADOW_RUN_TASK_NAME"
    const val installShadowDistPath = ":$SHADOW_INSTALL_TASK_NAME"
    const val shadowDistZipPath = ":shadowDistZip"

    val entriesInA = arrayOf("a.properties", "a2.properties")
    val entriesInB = arrayOf("b.properties")
    val entriesInAB = entriesInA + entriesInB
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

    // TODO: enable this flag for all tests once we have fixed all issues with isolated projects.
    //  See https://github.com/GradleUp/shadow/pull/1139.
    const val ipArgument = "-Dorg.gradle.unsafe.isolated-projects=true"
    const val infoArgument = "--info"

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
