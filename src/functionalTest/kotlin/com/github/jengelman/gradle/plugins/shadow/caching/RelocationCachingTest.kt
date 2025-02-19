package com.github.jengelman.gradle.plugins.shadow.caching

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

    assertCompositeExecutions {
      containsEntries(
        "my/Main.class",
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
      containsEntries(
        "my/Main.class",
        *shadowedEntries,
      )
      doesNotContainEntries(
        *junitEntries.filter { it.startsWith("junit/framework/") }.toTypedArray(),
      )
    }
  }
}
