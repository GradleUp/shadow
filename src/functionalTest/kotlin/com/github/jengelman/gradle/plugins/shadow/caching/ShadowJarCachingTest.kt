package com.github.jengelman.gradle.plugins.shadow.caching

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.internal.MinimizeDependencyFilter
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.util.containsOnly
import com.github.jengelman.gradle.plugins.shadow.util.getMainAttr
import kotlin.io.path.appendText
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.gradle.api.file.DuplicatesStrategy
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class ShadowJarCachingTest : BaseCachingTest() {
  /**
   * Ensure that a basic usage reuses an output from cache and then gets a cache miss when the content changes.
   */
  @Test
  fun shadowJarIsCachedCorrectlyWhenCopying() {
    projectScriptPath.appendText(
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

    val replaced = projectScriptPath.readText().lines()
      .filterNot { it == implementationFiles(artifactBJar) }
      .joinToString(lineSeparator)
    projectScriptPath.writeText(replaced)

    assertCompositeExecutions {
      containsOnly(
        *entriesInA,
        *manifestEntries,
      )
    }
  }

  @Test
  fun shadowJarIsCachedCorrectlyWhenOutputFileIsChanged() {
    projectScriptPath.appendText(
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

    projectScriptPath.appendText(
      """
        $shadowJar {
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
  fun shadowJarIsCachedCorrectlyAfterDependencyFilterChanged() {
    publishArtifactCD()
    projectScriptPath.appendText(
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

    projectScriptPath.appendText(
      """
        $shadowJar {
          dependencyFilter = new ${MinimizeDependencyFilter::class.java.name}(project)
        }
      """.trimIndent(),
    )

    assertions()
  }

  @Test
  fun shadowJarIsCachedCorrectlyAfterDuplicatesStrategyChanged() {
    listOf(
      DuplicatesStrategy.EXCLUDE,
      DuplicatesStrategy.INCLUDE,
      DuplicatesStrategy.WARN,
    ).forEach { strategy ->
      projectScriptPath.writeText(
        getDefaultProjectBuildScript(withGroup = true, withVersion = true) +
          """
            $shadowJar {
              duplicatesStrategy = DuplicatesStrategy.$strategy
            }
          """.trimIndent(),
      )

      assertCompositeExecutions()
    }
  }

  @Test
  fun shadowJarIsCachedCorrectlyAfterManifestAttrsChanged() {
    projectScriptPath.appendText(
      """
        jar {
          manifest {
            attributes 'Foo': 'Foo1'
          }
        }
        $shadowJar {
          manifest {
            attributes 'Bar': 'Bar1'
          }
        }
      """.trimIndent(),
    )

    assertCompositeExecutions {
      getMainAttr("Foo").isEqualTo("Foo1")
      getMainAttr("Bar").isEqualTo("Bar1")
    }

    var replaced = projectScriptPath.readText().replace("'Foo': 'Foo1'", "'Foo': 'Foo2'")
    projectScriptPath.writeText(replaced)

    assertCompositeExecutions {
      getMainAttr("Foo").isEqualTo("Foo2")
      getMainAttr("Bar").isEqualTo("Bar1")
    }

    replaced = projectScriptPath.readText().replace("'Bar': 'Bar1'", "'Bar': 'Bar2'")
    projectScriptPath.writeText(replaced)

    assertCompositeExecutions {
      getMainAttr("Foo").isEqualTo("Foo2")
      getMainAttr("Bar").isEqualTo("Bar2")
    }

    replaced = projectScriptPath.readText()
      .replace("'Foo': 'Foo2'", "'Foo': 'Foo3'")
      .replace("'Bar': 'Bar2'", "'Bar': 'Bar3'")
    projectScriptPath.writeText(replaced)

    assertCompositeExecutions {
      getMainAttr("Foo").isEqualTo("Foo3")
      getMainAttr("Bar").isEqualTo("Bar3")
    }
  }

  @Test
  fun shadowJarIsCachedCorrectlyAfterKotlinMainRunChanged() {
    val mainClassName = "my.Main"
    val main2ClassName = "my.Main2"

    val projectBuildScript = getDefaultProjectBuildScript(
      plugin = "org.jetbrains.kotlin.multiplatform",
      withGroup = true,
      withVersion = true,
    )
    projectScriptPath.writeText(
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

    val replaced = projectScriptPath.readText().replace(mainClassName, main2ClassName)
    projectScriptPath.writeText(replaced)

    assertCompositeExecutions {
      getMainAttr(mainClassAttributeKey).isEqualTo(main2ClassName)
    }
  }

  @Disabled("TODO: https://github.com/GradleUp/shadow/pull/1601#discussion_r2260096815")
  @Test
  fun shadowJarIsCachedCorrectlyAfterApplicationChanged() {
    val mainClassName = "my.Main"
    val main2ClassName = "my.Main2"

    projectScriptPath.appendText(
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

    val replaced = projectScriptPath.readText().replace(mainClassName, main2ClassName)
    projectScriptPath.writeText(replaced)

    assertCompositeExecutions {
      getMainAttr(mainClassAttributeKey).isEqualTo(main2ClassName)
    }
  }
}
