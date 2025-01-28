package com.github.jengelman.gradle.plugins.shadow.caching

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.doesNotContainEntries
import com.github.jengelman.gradle.plugins.shadow.util.isRegular
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
        $shadowJar {
          ${fromJar(artifactJar, projectJar)}
        }
      """.trimIndent(),
    )

    assertFirstExecutionSuccess()
    assertExecutionsAreCachedAndUpToDate()

    val replaced = projectScriptPath.readText().lines()
      .filterNot { it == fromJar(projectJar) }
      .joinToString(System.lineSeparator())
    projectScriptPath.writeText(replaced)

    assertFirstExecutionSuccess()
    assertExecutionsAreCachedAndUpToDate()
  }

  @Test
  fun shadowJarIsCachedCorrectlyWhenOutputFileIsChanged() {
    projectScriptPath.appendText(
      """
        $shadowJar {
          ${fromJar(artifactJar, projectJar)}
        }
      """.trimIndent() + System.lineSeparator(),
    )

    assertFirstExecutionSuccess()
    assertThat(outputShadowJar).isRegular()

    projectScriptPath.appendText(
      """
        $shadowJar {
          archiveBaseName = "foo"
        }
      """.trimIndent(),
    )

    assertExecutionsAreCachedAndUpToDate()
    assertThat(jarPath("build/libs/foo-1.0-all.jar")).isRegular()
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

    // First run successful with all files.
    assertFirstExecutionSuccess()
    assertThat(outputShadowJar).useAll {
      containsEntries(
        "shadow/Main.class",
        "shadow/Main2.class",
        "a.properties",
        "a2.properties",
        "b.properties",
      )
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
          exclude '**.properties'
        }
      """.trimIndent() + System.lineSeparator(),
    )
    // Second run successful after excludes changed.
    assertFirstExecutionSuccess()
    assertThat(outputShadowJar).useAll {
      containsEntries(
        "shadow/Main.class",
        "shadow/Main2.class",
      )
      doesNotContainEntries(
        "a.properties",
        "a2.properties",
        "b.properties",
      )
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
          include 'shadow/Main.class'
        }
      """.trimIndent() + System.lineSeparator(),
    )
    // Third run successful after includes changed.
    assertFirstExecutionSuccess()
    assertThat(outputShadowJar).useAll {
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
    // Forth run successful after includes changed again.
    assertFirstExecutionSuccess()
    assertThat(outputShadowJar).useAll {
      containsEntries(
        "shadow/Main.class",
        "shadow/Main2.class",
      )
      doesNotContainEntries(
        "a.properties",
        "a2.properties",
        "b.properties",
      )
    }

    // Clean and run 2 more times to ensure the states are cached and up-to-date.
    assertExecutionsAreCachedAndUpToDate()
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

    assertFirstExecutionSuccess()
    assertThat(outputShadowJar).useAll {
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
    val assertions = {
      assertThat(outputShadowJar).useAll {
        containsEntries(
          "shadow/Main.class",
        )
        doesNotContainEntries(
          "junit/framework/Test.class",
        )
      }
    }

    assertFirstExecutionSuccess()
    assertions()
    assertExecutionsAreCachedAndUpToDate()
    assertions()
  }
}
