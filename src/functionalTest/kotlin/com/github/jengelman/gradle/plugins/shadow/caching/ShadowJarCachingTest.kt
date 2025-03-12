package com.github.jengelman.gradle.plugins.shadow.caching

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.internal.MinimizeDependencyFilter
import com.github.jengelman.gradle.plugins.shadow.util.containsAtLeast
import com.github.jengelman.gradle.plugins.shadow.util.containsNone
import kotlin.io.path.appendText
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.gradle.api.file.DuplicatesStrategy
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
      containsAtLeast(*entriesInAB)
    }

    val replaced = projectScriptPath.readText().lines()
      .filterNot { it == implementationFiles(artifactBJar) }
      .joinToString(System.lineSeparator())
    projectScriptPath.writeText(replaced)

    assertCompositeExecutions {
      containsAtLeast(*entriesInA)
      containsNone(*entriesInB)
    }
  }

  @Test
  fun shadowJarIsCachedCorrectlyWhenOutputFileIsChanged() {
    projectScriptPath.appendText(
      """
        dependencies {
          ${implementationFiles(artifactAJar, artifactBJar)}
        }
      """.trimIndent() + System.lineSeparator(),
    )

    assertCompositeExecutions {
      containsAtLeast(*entriesInAB)
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
      containsAtLeast(*entriesInAB)
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
      """.trimIndent() + System.lineSeparator(),
    )
    val assertions = {
      assertCompositeExecutions {
        containsAtLeast(
          "c.properties",
          "d.properties",
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
      projectScriptPath.appendText(
        """
          $shadowJar {
            duplicatesStrategy = DuplicatesStrategy.$strategy
          }
        """.trimIndent() + System.lineSeparator(),
      )

      assertCompositeExecutions()
    }
  }
}
