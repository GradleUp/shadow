package com.github.jengelman.gradle.plugins.shadow.caching

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.internal.MinimizeDependencyFilter
import com.github.jengelman.gradle.plugins.shadow.util.containsOnly
import com.github.jengelman.gradle.plugins.shadow.util.getMainAttr
import kotlin.io.path.appendText
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.gradle.api.file.DuplicatesStrategy
import org.junit.jupiter.api.Test

class JavaPluginCachingTest : BaseCachingTest() {
  /**
   * Ensure that a basic usage reuses an output from cache and then gets a cache miss when the content changes.
   */
  @Test
  fun dependenciesChanged() {
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
  fun outputFileChanged() {
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
  fun dependencyFilterChanged() {
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
  fun duplicatesStrategyChanged() {
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
  fun manifestAttrsChanged() {
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

    val assertions = { valueFoo: String, valueBar: String ->
      assertCompositeExecutions {
        getMainAttr("Foo").isEqualTo(valueFoo)
        getMainAttr("Bar").isEqualTo(valueBar)
      }
    }

    assertions("Foo1", "Bar1")

    var replaced = projectScriptPath.readText().replace("Foo1", "Foo2")
    projectScriptPath.writeText(replaced)

    assertions("Foo2", "Bar1")

    replaced = projectScriptPath.readText().replace("Bar1", "Bar2")
    projectScriptPath.writeText(replaced)

    assertions("Foo2", "Bar2")

    replaced = projectScriptPath.readText()
      .replace("Foo2", "Foo3")
      .replace("Bar2", "Bar3")
    projectScriptPath.writeText(replaced)

    assertions("Foo3", "Bar3")
  }
}
