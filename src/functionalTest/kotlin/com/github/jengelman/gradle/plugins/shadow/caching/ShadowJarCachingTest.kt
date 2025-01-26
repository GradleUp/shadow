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

    val replaced = projectScriptPath.readText().lines().filter {
      it != fromJar(projectJar)
    }.joinToString(System.lineSeparator())
    projectScriptPath.writeText(replaced)
    assertFirstExecutionSuccess()
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
    writeMainClass()
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'shadow:a:1.0'
          implementation 'shadow:b:1.0'
        }
      """.trimIndent() + System.lineSeparator(),
    )

    assertFirstExecutionSuccess()
    assertThat(outputShadowJar).useAll {
      containsEntries(
        "shadow/Main.class",
        "a.properties",
        "a2.properties",
        "b.properties",
      )
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
          exclude 'b.properties'
        }
      """.trimIndent(),
    )
    assertFirstExecutionSuccess()
    assertThat(outputShadowJar).useAll {
      containsEntries(
        "shadow/Main.class",
        "a.properties",
        "a2.properties",
      )
      doesNotContainEntries(
        "b.properties",
      )
    }

    assertExecutionsAreCachedAndUpToDate()
    assertThat(outputShadowJar).useAll {
      containsEntries(
        "shadow/Main.class",
        "a.properties",
        "a2.properties",
      )
      doesNotContainEntries(
        "b.properties",
      )
    }
  }

  @Test
  fun shadowJarIsCachedCorrectlyWhenUsingDependencyIncludesExcludes() {
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
      """.trimIndent() + System.lineSeparator(),
    )

    writeMainClass(withImports = true)

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
    assertFirstExecutionSuccess()
    assertThat(outputShadowJar).useAll {
      containsEntries(
        "shadow/Main.class",
      )
      doesNotContainEntries(
        "junit/framework/Test.class",
      )
    }

    assertExecutionsAreCachedAndUpToDate()
    assertThat(outputShadowJar).useAll {
      containsEntries(
        "shadow/Main.class",
      )
      doesNotContainEntries(
        "junit/framework/Test.class",
      )
    }
  }
}
