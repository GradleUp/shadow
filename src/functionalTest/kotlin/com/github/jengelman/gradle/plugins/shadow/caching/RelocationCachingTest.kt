package com.github.jengelman.gradle.plugins.shadow.caching

import com.github.jengelman.gradle.plugins.shadow.util.containsOnly
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
      containsOnly(
        "my/",
        mainClassEntry,
        *junitEntries,
        *manifestEntries,
      )
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
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
}
