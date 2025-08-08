package com.github.jengelman.gradle.plugins.shadow

import assertk.Assert
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.internal.MinimizeDependencyFilter
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.JarPath
import com.github.jengelman.gradle.plugins.shadow.util.containsOnly
import com.github.jengelman.gradle.plugins.shadow.util.getMainAttr
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.appendText
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

  /**
   * Ensure that a basic usage reuses an output from cache and then gets a cache miss when the content changes.
   */
  @Test
  fun dependenciesChanged() {
    projectScript.appendText(
      """
        dependencies {
          ${implementationFiles(artifactAJar, artifactBJar)}
        }
      """.trimIndent(),
    )

    assertCompositeExecutions {
      containsOnly(
        *entriesInAB,
        *manifestEntries,
      )
    }

    val replaced = projectScript.readText().replace(implementationFiles(artifactBJar), "")
    projectScript.writeText(replaced)

    assertCompositeExecutions {
      containsOnly(
        *entriesInA,
        *manifestEntries,
      )
    }
  }

  @Test
  fun outputFileChanged() {
    projectScript.appendText(
      """
        dependencies {
          ${implementationFiles(artifactAJar, artifactBJar)}
        }
      """.trimIndent() + lineSeparator,
    )

    assertCompositeExecutions {
      containsOnly(
        *entriesInAB,
        *manifestEntries,
      )
    }

    projectScript.appendText(
      """
        $shadowJarTask {
          archiveBaseName = "foo"
        }
      """.trimIndent(),
    )

    assertExecutionsFromCacheAndUpToDate()
    assertThat(jarPath("build/libs/foo-1.0-all.jar")).useAll {
      containsOnly(
        *entriesInAB,
        *manifestEntries,
      )
    }
  }

  @Test
  fun dependencyFilterChanged() {
    publishArtifactCD()
    projectScript.appendText(
      """
        dependencies {
          implementation 'my:d:1.0'
        }
      """.trimIndent() + lineSeparator,
    )
    val assertions = {
      assertCompositeExecutions {
        containsOnly(
          "c.properties",
          "d.properties",
          *manifestEntries,
        )
      }
    }

    assertions()

    projectScript.appendText(
      """
        $shadowJarTask {
          dependencyFilter = new ${MinimizeDependencyFilter::class.java.name}(project)
        }
      """.trimIndent(),
    )

    assertions()
  }

  @Test
  fun duplicatesStrategyChanged() {
    listOf(
      DuplicatesStrategy.EXCLUDE,
      DuplicatesStrategy.INCLUDE,
      DuplicatesStrategy.WARN,
    ).forEach { strategy ->
      projectScript.writeText(
        getDefaultProjectBuildScript(withGroup = true, withVersion = true) +
          """
            $shadowJarTask {
              duplicatesStrategy = DuplicatesStrategy.$strategy
            }
          """.trimIndent(),
      )

      assertCompositeExecutions()
    }
  }

  @Test
  fun manifestAttrsChanged() {
    projectScript.appendText(
      """
        $jarTask {
          manifest {
            attributes 'Foo': 'Foo1'
          }
        }
        $shadowJarTask {
          manifest {
            attributes 'Bar': 'Bar1'
          }
        }
      """.trimIndent(),
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

    replaced = projectScript.readText()
      .replace("Foo2", "Foo3")
      .replace("Bar2", "Bar3")
    projectScript.writeText(replaced)

    assertions("Foo3", "Bar3")
  }

  @Test
  fun kotlinMainRunChanged() {
    val mainClassName = "my.Main"
    val main2ClassName = "my.Main2"

    val projectBuildScript = getDefaultProjectBuildScript(
      plugin = "org.jetbrains.kotlin.multiplatform",
      withGroup = true,
      withVersion = true,
    )
    projectScript.writeText(
      """
        $projectBuildScript
        kotlin {
          jvm().mainRun {
            it.mainClass.set('$mainClassName')
          }
        }
      """.trimIndent(),
    )

    assertCompositeExecutions {
      getMainAttr(mainClassAttributeKey).isEqualTo(mainClassName)
    }

    val replaced = projectScript.readText().replace(mainClassName, main2ClassName)
    projectScript.writeText(replaced)

    assertCompositeExecutions {
      getMainAttr(mainClassAttributeKey).isEqualTo(main2ClassName)
    }
  }

  @Test
  fun applicationChanged() {
    val mainClassName = "my.Main"
    val main2ClassName = "my.Main2"

    projectScript.appendText(
      """
        apply plugin: 'application'
        application {
          mainClass = '$mainClassName'
        }
      """.trimIndent(),
    )

    assertCompositeExecutions {
      getMainAttr(mainClassAttributeKey).isEqualTo(mainClassName)
    }

    val replaced = projectScript.readText().replace(mainClassName, main2ClassName)
    projectScript.writeText(replaced)

    assertCompositeExecutions {
      getMainAttr(mainClassAttributeKey).isEqualTo(main2ClassName)
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/717",
  )
  @Test
  fun jarIncludesExcludesChanged() {
    val mainClassEntry = writeClass(className = "Main")
    val main2ClassEntry = writeClass(className = "Main2")
    projectScript.appendText(
      """
        dependencies {
          implementation 'my:a:1.0'
          implementation 'my:b:1.0'
        }
      """.trimIndent() + lineSeparator,
    )

    assertCompositeExecutions {
      containsOnly(
        "my/",
        mainClassEntry,
        main2ClassEntry,
        *entriesInAB,
        *manifestEntries,
      )
    }

    projectScript.appendText(
      """
        $shadowJarTask {
          exclude '**.properties'
        }
      """.trimIndent() + lineSeparator,
    )

    assertCompositeExecutions {
      containsOnly(
        "my/",
        mainClassEntry,
        main2ClassEntry,
        *manifestEntries,
      )
    }

    projectScript.appendText(
      """
        $shadowJarTask {
          include '$mainClassEntry'
        }
      """.trimIndent() + lineSeparator,
    )

    assertCompositeExecutions {
      containsOnly(
        "my/",
        mainClassEntry,
        *manifestEntries,
      )
    }

    projectScript.appendText(
      """
        $shadowJarTask {
          include '$main2ClassEntry'
        }
      """.trimIndent() + lineSeparator,
    )

    assertCompositeExecutions {
      containsOnly(
        "my/",
        mainClassEntry,
        main2ClassEntry,
        *manifestEntries,
      )
    }
  }

  @Test
  fun dependenciesIncludesExcludesChanged() {
    val mainClassEntry = writeClass(withImports = true)
    projectScript.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
      """.trimIndent() + lineSeparator,
    )

    assertCompositeExecutions {
      containsOnly(
        "my/",
        mainClassEntry,
        *junitEntries,
        *manifestEntries,
      )
    }

    projectScript.appendText(
      """
        $shadowJarTask {
          dependencies {
            exclude(dependency('junit:junit'))
          }
        }
      """.trimIndent(),
    )

    assertCompositeExecutions {
      containsOnly(
        "my/",
        mainClassEntry,
        *manifestEntries,
      )
    }
  }

  @Test
  fun minimizeChanged() {
    taskPath = serverShadowJarPath

    writeClientAndServerModules()
    path("server/src/main/java/server/Server.java").writeText(
      """
        package server;
        public class Server {}
      """.trimIndent(),
    )

    assertCompositeExecutions(
      jarPathProvider = { outputServerShadowedJar },
    ) {
      containsOnly(
        "client/",
        "server/",
        "client/Client.class",
        "server/Server.class",
        *junitEntries,
        *manifestEntries,
      )
    }

    path("server/build.gradle").appendText(
      """
        $shadowJarTask {
          minimize {
            exclude(dependency('junit:junit:.*'))
          }
        }
      """.trimIndent(),
    )

    assertCompositeExecutions(
      jarPathProvider = { outputServerShadowedJar },
    ) {
      containsOnly(
        "server/",
        "server/Server.class",
        *junitEntries,
        *manifestEntries,
      )
    }
  }

  /**
   * Ensure that we get a cache miss when relocation changes and that caching works with relocation
   */
  @Test
  fun relocatorAdded() {
    projectScript.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
      """.trimIndent() + lineSeparator,
    )
    val mainClassEntry = writeClass(withImports = true)

    assertCompositeExecutions {
      containsOnly(
        "my/",
        mainClassEntry,
        *junitEntries,
        *manifestEntries,
      )
    }

    projectScript.appendText(
      """
        $shadowJarTask {
          relocate 'junit.framework', 'foo.junit.framework'
        }
      """.trimIndent(),
    )
    val relocatedEntries = junitEntries
      .map { it.replace("junit/framework/", "foo/junit/framework/") }.toTypedArray()

    assertCompositeExecutions {
      containsOnly(
        "my/",
        "foo/",
        "foo/junit/",
        mainClassEntry,
        *relocatedEntries,
        *manifestEntries,
      )
    }
  }

  @Test
  fun serviceFileTransformerPropsChanged() {
    val mainClassEntry = writeClass()
    val assertions = {
      assertCompositeExecutions {
        containsOnly(
          "my/",
          mainClassEntry,
          *manifestEntries,
        )
      }
    }

    assertions()

    projectScript.appendText(
      transform<ServiceFileTransformer>(
        transformerBlock = """
          path = 'META-INF/foo'
        """.trimIndent(),
      ),
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
        $shadowJarTask {
          mergeServiceFiles()
        }
      """.trimIndent() + lineSeparator,
    )

    assertCompositeExecutions()

    projectScript.appendText(
      """
        $shadowJarTask {
          mergeGroovyExtensionModules()
        }
      """.trimIndent() + lineSeparator,
    )

    assertCompositeExecutions()

    projectScript.appendText(
      """
        $shadowJarTask {
          // Use Transformer.Companion (no-op) to mock a custom transformer here, it's not cacheable.
          transform(${ResourceTransformer.Companion::class.java.name})
        }
      """.trimIndent(),
    )

    assertExecutionSuccess()
    cleanOutputs()
    // The shadowJar task should be executed again as the cache is disabled.
    assertExecutionSuccess()
  }

  private fun cleanOutputs() {
    run("clean")
    @OptIn(ExperimentalPathApi::class)
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
   * Combines [assertExecutionSuccess] and [assertExecutionsFromCacheAndUpToDate] for simplifying assertions.
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
    val result = run(taskPath)
    assertThat(result).taskOutcomeEquals(taskPath, expectedOutcome)
  }
}
