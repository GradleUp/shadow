package com.github.jengelman.gradle.plugins.shadow.caching

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.doesNotContainEntries
import kotlin.io.path.appendText
import org.junit.jupiter.api.Test

class RelocationCachingTest : BaseCachingTest() {
  /**
   * Ensure that we get a cache miss when relocation changes and that caching works with relocation
   */
  @Test
  fun shadowJarIsCachedCorrectlyWhenRelocationIsAdded() {
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
      """.trimIndent() + System.lineSeparator(),
    )
    writeMainClass(withImports = true)

    assertExecutionSuccess()
    assertThat(outputShadowJar).useAll {
      containsEntries(
        "shadow/Main.class",
        "junit/framework/Test.class",
      )
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
          relocate 'junit.framework', 'foo.junit.framework'
        }
      """.trimIndent(),
    )
    val assertions = {
      assertThat(outputShadowJar).useAll {
        containsEntries(
          "shadow/Main.class",
          "foo/junit/framework/Test.class",
        )
        doesNotContainEntries(
          "junit/framework/Test.class",
        )
      }
    }

    assertExecutionSuccess()
    assertions()
    assertExecutionsFromCacheAndUpToDate()
    assertions()
  }
}
