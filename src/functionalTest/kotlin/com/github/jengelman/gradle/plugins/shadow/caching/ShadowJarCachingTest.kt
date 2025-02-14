package com.github.jengelman.gradle.plugins.shadow.caching

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.internal.MinimizeDependencyFilter
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.doesNotContainEntries
import kotlin.io.path.appendText
import kotlin.io.path.readText
import kotlin.io.path.writeText
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
      containsEntries(*entriesInAB)
    }

    val replaced = projectScriptPath.readText().lines()
      .filterNot { it == implementationFiles(artifactBJar) }
      .joinToString(System.lineSeparator())
    projectScriptPath.writeText(replaced)

    assertCompositeExecutions {
      containsEntries(*entriesInA)
      doesNotContainEntries(*entriesInB)
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
      containsEntries(*entriesInAB)
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
      containsEntries(*entriesInAB)
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/717",
  )
  @Test
  fun shadowJarIsCachedCorrectlyWhenUsingIncludesExcludes() {
    writeMainClass(className = "Main")
    writeMainClass(className = "Main2")
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'shadow:a:1.0'
          implementation 'shadow:b:1.0'
        }
      """.trimIndent() + System.lineSeparator(),
    )

    assertCompositeExecutions {
      val entries = entriesInAB + arrayOf("shadow/Main.class", "shadow/Main2.class")
      containsEntries(*entries)
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
          exclude '**.properties'
        }
      """.trimIndent() + System.lineSeparator(),
    )

    assertCompositeExecutions {
      containsEntries(
        "shadow/Main.class",
        "shadow/Main2.class",
      )
      doesNotContainEntries(*entriesInAB)
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
          include 'shadow/Main.class'
        }
      """.trimIndent() + System.lineSeparator(),
    )

    assertCompositeExecutions {
      containsEntries(
        "shadow/Main.class",
      )
      doesNotContainEntries(
        "shadow/Main2.class",
        "a.properties",
        "a2.properties",
        "b.properties",
      )
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
          include 'shadow/Main2.class'
        }
      """.trimIndent() + System.lineSeparator(),
    )

    assertCompositeExecutions {
      containsEntries(
        "shadow/Main.class",
        "shadow/Main2.class",
      )
      doesNotContainEntries(*entriesInAB)
    }
  }

  @Test
  fun shadowJarIsCachedCorrectlyWhenUsingDependencyIncludesExcludes() {
    writeMainClass(withImports = true)
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
      """.trimIndent() + System.lineSeparator(),
    )

    assertCompositeExecutions {
      containsEntries(
        "shadow/Main.class",
        "junit/framework/Test.class",
      )
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
          dependencies {
            exclude(dependency('junit:junit'))
          }
        }
      """.trimIndent(),
    )

    assertCompositeExecutions {
      containsEntries(
        "shadow/Main.class",
      )
      doesNotContainEntries(
        "junit/framework/Test.class",
      )
    }
  }

  @Test
  fun shadowJarIsCachedCorrectlyAfterDependencyFilterChanged() {
    publishArtifactCD()
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'shadow:d:1.0'
        }
      """.trimIndent() + System.lineSeparator(),
    )
    val assertions = {
      assertCompositeExecutions {
        containsEntries(
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
}
