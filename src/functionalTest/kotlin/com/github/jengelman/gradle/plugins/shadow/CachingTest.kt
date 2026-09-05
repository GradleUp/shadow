package com.github.jengelman.gradle.plugins.shadow

import assertk.Assert
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.getMainAttr
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import kotlin.io.path.appendText
import kotlin.io.path.deleteExisting
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

class CachingTest : BasePluginTest() {
  private var taskPath: String = shadowJarPath

  @Test
  fun dependenciesChanged() {
    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'my:a:1.0'
      |  implementation 'my:b:1.0'
      |}
      """
        .trimMargin()
    )

    assertCompositeExecutions {
      containsOnly(*entriesInAB, "META-INF/", "META-INF/MANIFEST.MF")
    }

    val replaced = projectScript.readText().replace("implementation 'my:b:1.0'", "")
    projectScript.writeText(replaced)

    assertCompositeExecutions { containsOnly(*entriesInA, "META-INF/", "META-INF/MANIFEST.MF") }
  }

  @Test
  fun outputFileChanged() {
    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'my:a:1.0'
      |  implementation 'my:b:1.0'
      |}
      |"""
        .trimMargin()
    )

    assertCompositeExecutions {
      containsOnly(*entriesInAB, "META-INF/", "META-INF/MANIFEST.MF")
    }

    projectScript.appendText(
      """
      |$shadowJarTask {
      |  archiveBaseName = "foo"
      |}
      """
        .trimMargin()
    )

    assertExecutionsFromCacheAndUpToDate()
    assertThat(jarPath("build/libs/foo-1.0-all.jar")).useAll {
      containsOnly(*entriesInAB, "META-INF/", "META-INF/MANIFEST.MF")
    }
  }

  @Test
  fun duplicatesStrategyChanged() {
    listOf(DuplicatesStrategy.EXCLUDE, DuplicatesStrategy.INCLUDE, DuplicatesStrategy.WARN)
      .forEach { strategy ->
        projectScript.writeText(
          """
          |${getDefaultProjectBuildScript()}
          |$shadowJarTask {
          |  duplicatesStrategy = DuplicatesStrategy.$strategy
          |}
          """
            .trimMargin()
        )

        assertCompositeExecutions()
      }
  }

  @Test
  fun manifestAttrsChanged() {
    projectScript.appendText(
      """
      |$jarTask {
      |  manifest {
      |    attributes 'Foo': 'Foo1'
      |  }
      |}
      |$shadowJarTask {
      |  manifest {
      |    attributes 'Bar': 'Bar1'
      |  }
      |}
      """
        .trimMargin()
    )

    val assertions = { valueFoo: String, valueBar: String ->
      assertCompositeExecutions {
        getMainAttr("Foo").isEqualTo(valueFoo)
        getMainAttr("Bar").isEqualTo(valueBar)
      }
    }

    assertions("Foo1", "Bar1")

    var replaced = projectScript.readText().replace("Foo1", "Foo2")
    projectScript.writeText(replaced)

    assertions("Foo2", "Bar1")

    replaced = projectScript.readText().replace("Bar1", "Bar2")
    projectScript.writeText(replaced)

    assertions("Foo2", "Bar2")

    replaced = projectScript.readText().replace("Foo2", "Foo3").replace("Bar2", "Bar3")
    projectScript.writeText(replaced)

    assertions("Foo3", "Bar3")
  }

  @Test
  fun kotlinMainRunChanged() {
    val mainClassName = "my.Main"
    val main2ClassName = "my.Main2"

    projectScript.writeText(
      """
      |${getDefaultProjectBuildScript(plugin = "org.jetbrains.kotlin.multiplatform")}
      |kotlin {
      |  jvm().mainRun {
      |    it.mainClass.set('$mainClassName')
      |  }
      |}
      """
        .trimMargin()
    )

    assertCompositeExecutions { getMainAttr(mainClassAttributeKey).isEqualTo(mainClassName) }

    val replaced = projectScript.readText().replace(mainClassName, main2ClassName)
    projectScript.writeText(replaced)

    assertCompositeExecutions { getMainAttr(mainClassAttributeKey).isEqualTo(main2ClassName) }
  }

  @Test
  fun applicationChanged() {
    val mainClassName = "my.Main"
    val main2ClassName = "my.Main2"

    projectScript.appendText(
      """
      |apply plugin: 'application'
      |application {
      |  mainClass = '$mainClassName'
      |}
      """
        .trimMargin()
    )

    assertCompositeExecutions { getMainAttr(mainClassAttributeKey).isEqualTo(mainClassName) }

    val replaced = projectScript.readText().replace(mainClassName, main2ClassName)
    projectScript.writeText(replaced)

    assertCompositeExecutions { getMainAttr(mainClassAttributeKey).isEqualTo(main2ClassName) }
  }

  @Test // #717
  fun jarIncludesExcludesChanged() {
    val mainClassEntry = writeClass(className = "Main")
    val main2ClassEntry = writeClass(className = "Main2")
    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'my:a:1.0'
      |  implementation 'my:b:1.0'
      |}
      |"""
        .trimMargin()
    )

    assertCompositeExecutions {
      containsOnly(
        "my/",
        mainClassEntry,
        main2ClassEntry,
        *entriesInAB,
        "META-INF/",
        "META-INF/MANIFEST.MF",
      )
    }

    projectScript.appendText(
      """
      |$shadowJarTask {
      |  exclude '**.properties'
      |}
      |
      """
        .trimMargin()
    )

    assertCompositeExecutions {
      containsOnly(
        "my/",
        mainClassEntry,
        main2ClassEntry,
        "META-INF/",
        "META-INF/MANIFEST.MF",
      )
    }

    projectScript.appendText(
      """
      |$shadowJarTask {
      |  include '$mainClassEntry'
      |}
      |
      """
        .trimMargin()
    )

    assertCompositeExecutions {
      containsOnly("my/", mainClassEntry, "META-INF/", "META-INF/MANIFEST.MF")
    }

    projectScript.appendText(
      """
      |$shadowJarTask {
      |  include '$main2ClassEntry'
      |}
      |
      """
        .trimMargin()
    )

    assertCompositeExecutions {
      containsOnly(
        "my/",
        mainClassEntry,
        main2ClassEntry,
        "META-INF/",
        "META-INF/MANIFEST.MF",
      )
    }
  }

  @Test
  fun dependenciesIncludesExcludesChanged() {
    val mainClassEntry = writeClass(withImports = true)
    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'junit:junit:3.8.2'
      |}
      |"""
        .trimMargin()
    )

    assertCompositeExecutions {
      containsOnly(
        "my/",
        mainClassEntry,
        *junitEntries,
        "META-INF/",
        "META-INF/MANIFEST.MF",
      )
    }

    projectScript.appendText(
      """
      |$shadowJarTask {
      |  dependencies {
      |    exclude(dependency('junit:junit'))
      |  }
      |}
      """
        .trimMargin()
    )

    assertCompositeExecutions {
      containsOnly("my/", mainClassEntry, "META-INF/", "META-INF/MANIFEST.MF")
    }
  }

  @Test
  fun minimizeChanged() {
    taskPath = serverShadowJarPath

    writeClientAndServerModules()
    path("server/src/main/java/server/Server.java")
      .writeText(
        """
        |package server;
        |public class Server {}
        """
          .trimMargin()
      )

    assertCompositeExecutions(jarPathProvider = { outputServerShadowedJar }) {
      containsOnly(
        "client/",
        "server/",
        "client/Client.class",
        "server/Server.class",
        *junitEntries,
        "META-INF/",
        "META-INF/MANIFEST.MF",
      )
    }

    path("server/build.gradle")
      .appendText(
        """
        |$shadowJarTask {
        |  minimize {
        |    exclude(dependency('junit:junit:.*'))
        |  }
        |}
        """
          .trimMargin()
      )

    assertCompositeExecutions(jarPathProvider = { outputServerShadowedJar }) {
      containsOnly(
        "server/",
        "server/Server.class",
        *junitEntries,
        "META-INF/",
        "META-INF/MANIFEST.MF",
      )
    }
  }

  @Test
  fun r8KeepRuleFileChanged() {
    val previousTaskPath = taskPath
    taskPath = serverShadowJarPath
    try {
      writeR8ClientAndServerModules()
      val proguardRules = path("server/r8-rules.pro")
      proguardRules.deleteExisting()

      assertExecutionSuccess()
      assertThat(outputServerShadowedJar).useAll {
        containsOnly(
          "client/Used.class",
          "server/Server.class",
          "META-INF/MANIFEST.MF",
        )
      }

      proguardRules.writeText("-keep class client.Reflective { *; }")

      assertExecutionSuccess()
      assertThat(outputServerShadowedJar).useAll {
        containsOnly(
          "client/Used.class",
          "client/Reflective.class",
          "server/Server.class",
          "META-INF/MANIFEST.MF",
        )
      }
      assertExecutionsFromCacheAndUpToDate()
    } finally {
      taskPath = previousTaskPath
    }
  }

  @Test
  fun r8ClasspathRuleChanged() {
    val previousTaskPath = taskPath
    taskPath = serverShadowJarPath
    try {
      writeR8ClientAndServerModules()
      path("server/r8-rules.pro").deleteExisting()

      assertExecutionSuccess()
      assertThat(outputServerShadowedJar).useAll {
        containsOnly(
          "client/Used.class",
          "server/Server.class",
          "META-INF/MANIFEST.MF",
        )
      }

      path("client/src/main/resources/META-INF/proguard/client.pro")
        .writeText("-keep class client.Reflective { *; }")

      assertExecutionSuccess()
      assertThat(outputServerShadowedJar).useAll {
        containsOnly(
          "client/Used.class",
          "client/Reflective.class",
          "server/Server.class",
          "META-INF/proguard/client.pro",
          "META-INF/MANIFEST.MF",
        )
      }
      assertExecutionsFromCacheAndUpToDate()
    } finally {
      taskPath = previousTaskPath
    }
  }

  @Test
  fun relocatorChanged() {
    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'junit:junit:3.8.2'
      |}
      |"""
        .trimMargin()
    )
    val mainClassEntry = writeClass(withImports = true)

    assertCompositeExecutions {
      containsOnly(
        "my/",
        mainClassEntry,
        *junitEntries,
        "META-INF/",
        "META-INF/MANIFEST.MF",
      )
    }

    projectScript.appendText(
      """
      |$shadowJarTask {
      |  relocate 'junit.framework', 'foo.junit.framework'
      |}
      """
        .trimMargin()
    )
    val relocatedEntries =
      junitEntries.map { it.replace("junit/framework/", "foo/junit/framework/") }.toTypedArray()

    assertCompositeExecutions {
      containsOnly(
        "my/",
        "foo/",
        "foo/junit/",
        mainClassEntry,
        *relocatedEntries,
        "META-INF/",
        "META-INF/MANIFEST.MF",
      )
    }
  }

  @Test // #1932
  fun relocatorPatternChanged() {
    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'junit:junit:3.8.2'
      |}
      |$shadowJarTask {
      |  relocate 'junit.framework', 'foo.junit.framework'
      |}
      |
      """
        .trimMargin()
    )
    val mainClassEntry = writeClass(withImports = true)
    val fooEntries =
      junitEntries.map { it.replace("junit/framework/", "foo/junit/framework/") }.toTypedArray()

    assertCompositeExecutions {
      containsOnly(
        "my/",
        "foo/",
        "foo/junit/",
        mainClassEntry,
        *fooEntries,
        "META-INF/",
        "META-INF/MANIFEST.MF",
      )
    }

    val replaced = projectScript.readText().replace("foo.junit.framework", "bar.junit.framework")
    projectScript.writeText(replaced)
    val barEntries =
      junitEntries.map { it.replace("junit/framework/", "bar/junit/framework/") }.toTypedArray()

    assertCompositeExecutions {
      containsOnly(
        "my/",
        "bar/",
        "bar/junit/",
        mainClassEntry,
        *barEntries,
        "META-INF/",
        "META-INF/MANIFEST.MF",
      )
    }
  }

  @Test
  fun serviceFileTransformerPropsChanged() {
    val mainClassEntry = writeClass()
    val assertions = {
      assertCompositeExecutions {
        containsOnly("my/", mainClassEntry, "META-INF/", "META-INF/MANIFEST.MF")
      }
    }

    assertions()

    projectScript.appendText(
      transform<ServiceFileTransformer>(
        transformerBlock =
          """
          |path = 'META-INF/foo'
          """
            .trimMargin()
      )
    )

    assertions()

    val replaced = projectScript.readText().replace("META-INF/foo", "META-INF/bar")
    projectScript.writeText(replaced)

    assertions()
  }

  @Test
  fun disableCacheIfAnyTransformerIsNotCacheable() {
    projectScript.appendText(
      """
      |$shadowJarTask {
      |  mergeServiceFiles()
      |}
      |
      """
        .trimMargin()
    )

    assertCompositeExecutions()

    projectScript.appendText(
      """
      |${transform<GroovyExtensionModuleTransformer>()}
      |
      """
        .trimMargin()
    )

    assertCompositeExecutions()

    projectScript.appendText(
      """
      |$shadowJarTask {
      |  // Use Transformer.Companion (no-op) to mock a custom transformer here, it's not cacheable.
      |  transform(${ResourceTransformer.Companion::class.java.name})
      |}
      """
        .trimMargin()
    )

    assertExecutionSuccess()
    cleanOutputs()
    // The shadowJar task should be executed again as the cache is disabled.
    assertExecutionSuccess()
  }

  private fun cleanOutputs() {
    runWithSuccess("clean")
    val buildDirs = projectRoot.walk().filter { it.isDirectory() && it.name == "build" }
    // Make sure build folders are deleted by clean task.
    assertThat(buildDirs).isEmpty()
  }

  private fun assertExecutionSuccess() {
    // The task was executed and not pulled from cache.
    assertRunWithResult(TaskOutcome.SUCCESS)
  }

  /**
   * This should be called after [assertExecutionSuccess] to ensure that the [taskPath] is cached.
   */
  private fun assertExecutionsFromCacheAndUpToDate() {
    cleanOutputs()
    // Run the task again to ensure it is pulled from cache.
    assertRunWithResult(TaskOutcome.FROM_CACHE)
    // Run the task again to ensure it is up-to-date.
    assertRunWithResult(TaskOutcome.UP_TO_DATE)
  }

  /**
   * Combines [assertExecutionSuccess] and [assertExecutionsFromCacheAndUpToDate] for simplifying
   * assertions.
   */
  private fun assertCompositeExecutions(
    jarPathProvider: () -> JarPath = { outputShadowedJar },
    jarPathAssertions: Assert<JarPath>.() -> Unit = {},
  ) {
    // First run should execute.
    assertExecutionSuccess()
    assertThat(jarPathProvider()).useAll(jarPathAssertions)
    // Subsequent runs should be from cache and up-to-date after configurations changed.
    assertExecutionsFromCacheAndUpToDate()
  }

  private fun assertRunWithResult(expectedOutcome: TaskOutcome) {
    val result = runWithSuccess(taskPath)
    assertThat(result).taskOutcomeEquals(taskPath, expectedOutcome)
  }

  private fun writeR8ClientAndServerModules() {
    settingsScript.appendText(
      """
      |include 'client', 'server'
      """
        .trimMargin()
    )
    projectScript.deleteExisting()

    path("client/src/main/java/client/Used.java")
      .writeText(
        """
        |package client;
        |public class Used {
        |  public static String name() {
        |    return "used";
        |  }
        |}
        """
          .trimMargin()
      )
    path("client/src/main/java/client/Unused.java")
      .writeText(
        """
        |package client;
        |public class Unused {}
        """
          .trimMargin()
      )
    path("client/src/main/java/client/Reflective.java")
      .writeText(
        """
        |package client;
        |public class Reflective {}
        """
          .trimMargin()
      )
    path("client/build.gradle")
      .writeText(
        """
        |${getDefaultProjectBuildScript("java")}
        |
        """
          .trimMargin()
      )

    path("server/src/main/java/server/Server.java")
      .writeText(
        """
        |package server;
        |import client.Used;
        |public class Server {
        |  public String name() {
        |    return Used.name();
        |  }
        |}
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
        |  minimize {
        |    r8 {
        |      proguardRuleFiles.from(file("r8-rules.pro"))
        |    }
        |  }
        |}
        |
        """
          .trimMargin()
      )
  }
}
