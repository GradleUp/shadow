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
import com.github.jengelman.gradle.plugins.shadow.testkit.enableNoImplicitLookupInParentProjects
import com.github.jengelman.gradle.plugins.shadow.testkit.gradleRunner
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsPath
import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.util.AppendableMavenRepository
import com.github.jengelman.gradle.plugins.shadow.util.JarBuilder
import com.github.jengelman.gradle.plugins.shadow.util.JvmLang
import com.github.jengelman.gradle.plugins.shadow.util.createDefaultLocalMavenRepository
import java.io.Closeable
import java.nio.file.Path
import java.util.Properties
import java.util.jar.JarEntry
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.appendText
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.deleteExisting
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
import org.vafer.jdeb.shaded.objectweb.asm.ClassWriter
import org.vafer.jdeb.shaded.objectweb.asm.Opcodes

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

  lateinit var artifactGJar: Path
    private set

  val projectScript: Path
    get() = path("build.gradle")

  val settingsScript: Path
    get() = path("settings.gradle")

  val outputJar: JarPath
    get() = jarPath("build/libs/my-1.0.jar")

  open val outputShadowedJar: JarPath
    get() = jarPath("build/libs/my-1.0-all.jar")

  val outputShadowedSourcesJar: JarPath
    get() = jarPath("build/libs/my-1.0-all-sources.jar")

  val outputServerShadowedJar: JarPath
    get() = jarPath("server/build/libs/server-1.0-all.jar")

  val outputServerShadowedSourcesJar: JarPath
    get() = jarPath("server/build/libs/server-1.0-all-sources.jar")

  @BeforeAll
  fun beforeAll() {
    localRepo = createDefaultLocalMavenRepository(junitJar).apply { publish() }

    artifactAJar = path("my/a/1.0/a-1.0.jar", parent = localRepo.root)
    artifactBJar = path("my/b/1.0/b-1.0.jar", parent = localRepo.root)
    artifactGJar = path("my/g/1.0/g-1.0.jar", parent = localRepo.root)
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
    @OptIn(ExperimentalPathApi::class) localRepo.root.deleteRecursively()
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
           |plugins {
           |  id '$plugin'
           |  id '$shadowPluginId' apply $applyShadowPlugin
           |}
           |$groupInfo
           |$versionInfo
           |
           """
      .trimMargin()
  }

  fun getDefaultSettingsBuildScript(
    startBlock: String = "",
    // Use a test-specific build cache directory. This ensures that we'll only use cached outputs
    // generated during this test, and we won't accidentally use cached outputs from a different
    // test or a different build.
    // https://docs.gradle.org/current/userguide/build_cache.html#sec:build_cache_configure_local
    buildCacheBlock: String = "local { directory = file('build-cache') }",
    endBlock: String = "rootProject.name = 'my'",
  ): String {
    return """
           |$startBlock
           |dependencyResolutionManagement {
           |  repositories {
           |    maven { url = '${localRepo.root.toUri()}' }
           |    mavenCentral()
           |    google()
           |  }
           |}
           |buildCache {
           |  $buildCacheBlock
           |}
           |$enableNoImplicitLookupInParentProjects
           |enableFeaturePreview 'STABLE_CONFIGURATION_CACHE'
           |enableFeaturePreview 'TYPESAFE_PROJECT_ACCESSORS'
           |$endBlock
           |
           """
      .trimMargin()
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

  fun runWithSuccess(vararg arguments: String, block: GradleRunner.() -> Unit = {}): BuildResult {
    return runner(arguments = arguments.toList(), block = block)
      .build()
      .assertNoDeprecationWarnings()
  }

  fun runWithFailure(vararg arguments: String, block: GradleRunner.() -> Unit = {}): BuildResult {
    return runner(arguments = arguments.toList(), block = block)
      .buildAndFail()
      .assertNoDeprecationWarnings()
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
          |package $packageName;
          |$imports
          |public class $className {
          |  public static void main(String[] args) {
          |    if (args.length == 0) throw new IllegalArgumentException("No arguments provided.");
          |    String content = String.format("Hello, World! (%s) from $className", (Object[]) args);
          |    System.out.println(content);
          |    System.out.println($classRef);
          |  }
          |}
          """
            .trimMargin()
        }
        JvmLang.Kotlin -> {
          val imports = if (withImports) "import junit.framework.Test" else ""
          val classRef = if (withImports) "\"Refs: \" + Test::class.java.name" else "\"Refs: null\""
          """
          |@file:JvmName("$className")
          |package $packageName
          |$imports
          |fun main(vararg args: String) {
          |  if (args.isEmpty()) throw IllegalArgumentException("No arguments provided.")
          |  val content ="Hello, World! (%s) from $className".format(*args)
          |  println(content)
          |  println($classRef)
          |}
          """
            .trimMargin()
        }
      }
    },
  ): String {
    val basePath = "${packageName.replace('.', '/')}/$className"
    path("src/$sourceSet/$jvmLang/$basePath.${jvmLang.suffix}").writeText(content())
    return "$basePath.class"
  }

  fun writeClientAndServerModules(clientShadowed: Boolean = false, serverShadowBlock: String = "") {
    settingsScript.appendText(
      """
      |include 'client', 'server'
      """
        .trimMargin()
    )
    projectScript.deleteExisting()

    path("client/src/main/java/client/Client.java")
      .writeText(
        """
        |package client;
        |public class Client {}
        """
          .trimMargin()
      )
    path("client/build.gradle")
      .writeText(
        """
        |${getDefaultProjectBuildScript("java")}
        |java {
        |  withSourcesJar()
        |}
        |dependencies {
        |  implementation 'junit:junit:3.8.2'
        |}
        |
        """
          .trimMargin()
      )

    path("server/src/main/java/server/Server.java")
      .writeText(
        """
        |package server;
        |import client.Client;
        |public class Server {}
        """
          .trimMargin()
      )
    path("server/build.gradle")
      .writeText(
        """
        |${getDefaultProjectBuildScript("java")}
        |dependencies {
        |  implementation project(':client')
        |}
        |$shadowJarTask {
        |  $serverShadowBlock
        |}
        |
        """
          .trimMargin()
      )

    if (!clientShadowed) return
    path("client/build.gradle")
      .appendText(
        """
        |$shadowJarTask {
        |  relocate 'junit.framework', 'client.junit.framework'
        |}
        |
        """
          .trimMargin()
      )
    path("server/src/main/java/server/Server.java")
      .writeText(
        """
        |package server;
        |import client.Client;
        |import client.junit.framework.Test;
        |public class Server {}
        """
          .trimMargin()
      )
    val replaced =
      path("server/build.gradle")
        .readText()
        .replace("project(':client')", "project(path: ':client', configuration: 'shadow')")
    path("server/build.gradle").writeText(replaced)
  }

  fun writeGradlePluginModule() {
    projectScript.writeText(
      """
      |${getDefaultProjectBuildScript("java-gradle-plugin")}
      |gradlePlugin {
      |  plugins {
      |    create('my.plugin') {
      |      implementationClass = 'my.plugin.MyPlugin'
      |    }
      |  }
      |}
      |
      """
        .trimMargin()
    )

    path("src/main/java/my/plugin/MyPlugin.java")
      .writeText(
        """
        |package my.plugin;
        |import org.gradle.api.Plugin;
        |import org.gradle.api.Project;
        |public class MyPlugin implements Plugin<Project> {
        |  public void apply(Project project) {
        |    System.out.println("MyPlugin: Hello, World!");
        |  }
        |}
        """
          .trimMargin()
      )
  }

  private fun runner(arguments: Iterable<String>, block: GradleRunner.() -> Unit): GradleRunner {
    return gradleRunner(
      projectDir = projectRoot,
      arguments = commonGradleArgs + arguments,
      block = block,
    )
  }

  @Suppress("ConstPropertyName")
  companion object {
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
    val junitRawEntries: List<JarEntry> =
      JarPath(junitJar)
        .use { it.entries().toList() }
        .filterNot {
          // This entry is not present in the jar file.
          it.name == "junit3.8.2/"
        }
    val junitEntries: Array<String> = junitRawEntries.map { it.name }.toTypedArray()
    const val manifestEntry = "META-INF/MANIFEST.MF"
    val manifestEntries = arrayOf("META-INF/", manifestEntry)

    val shadowJarTask: String =
      "tasks.named('$SHADOW_JAR_TASK_NAME', ${ShadowJar::class.java.name})"
    const val runShadowTask = "tasks.named('$SHADOW_RUN_TASK_NAME', JavaExec)"
    const val jarTask = "tasks.named('jar', Jar)"

    const val infoArgument = "--info"

    fun String.toProperties(): Properties = Properties().apply { load(byteInputStream()) }

    fun implementationFiles(vararg paths: Path): String {
      return paths.joinToString("\n") {
        "implementation files('${it.invariantSeparatorsPathString}')"
      }
    }

    fun createEmptyClassBytes(internalName: String): ByteArray {
      return ClassWriter(0)
        .apply {
          visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null)
          visitEnd()
        }
        .toByteArray()
    }

    inline fun <reified T : ResourceTransformer> transform(
      dependenciesBlock: String = "",
      transformerBlock: String = "",
    ): String {
      return """
             |dependencies {
             |  $dependenciesBlock
             |}
             |$shadowJarTask {
             |  transform(${T::class.java.name}) {
             |    $transformerBlock
             |  }
             |}
             """
        .trimMargin()
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
