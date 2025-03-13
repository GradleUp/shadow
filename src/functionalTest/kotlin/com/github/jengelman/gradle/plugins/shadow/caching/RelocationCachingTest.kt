package com.github.jengelman.gradle.plugins.shadow.caching

import com.github.jengelman.gradle.plugins.shadow.util.containsAtLeast
import com.github.jengelman.gradle.plugins.shadow.util.containsNone
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
    val mainClassEntry = writeClass(withImports = true)

    assertCompositeExecutions {
      containsAtLeast(
        mainClassEntry,
        *junitEntries,
      )
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
          relocate 'junit.framework', 'foo.junit.framework'
        }
      """.trimIndent(),
    )
    val shadowedEntries = junitEntries
      .map { it.replace("junit/framework/", "foo/junit/framework/") }.toTypedArray()

    assertCompositeExecutions {
      containsAtLeast(
        mainClassEntry,
        *shadowedEntries,
      )
      containsNone(
        *junitEntries.filter { it.startsWith("junit/framework/") }.toTypedArray(),
      )
    }
  }
}
