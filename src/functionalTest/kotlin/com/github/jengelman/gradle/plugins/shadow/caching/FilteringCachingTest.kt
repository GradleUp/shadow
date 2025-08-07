package com.github.jengelman.gradle.plugins.shadow.caching

import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.containsOnly
import kotlin.io.path.appendText
import org.junit.jupiter.api.Test

class FilteringCachingTest : BaseCachingTest() {

  @Issue(
    "https://github.com/GradleUp/shadow/issues/717",
  )
  @Test
  fun jarIncludesExcludesChanged() {
    val mainClassEntry = writeClass(className = "Main")
    val main2ClassEntry = writeClass(className = "Main2")
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'my:a:1.0'
          implementation 'my:b:1.0'
        }
      """.trimIndent() + lineSeparator,
    )

    assertCompositeExecutions {
      containsOnly(
        "my/",
        mainClassEntry,
        main2ClassEntry,
        *entriesInAB,
        *manifestEntries,
      )
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
          exclude '**.properties'
        }
      """.trimIndent() + lineSeparator,
    )

    assertCompositeExecutions {
      containsOnly(
        "my/",
        mainClassEntry,
        main2ClassEntry,
        *manifestEntries,
      )
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
          include '$mainClassEntry'
        }
      """.trimIndent() + lineSeparator,
    )

    assertCompositeExecutions {
      containsOnly(
        "my/",
        mainClassEntry,
        *manifestEntries,
      )
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
          include '$main2ClassEntry'
        }
      """.trimIndent() + lineSeparator,
    )

    assertCompositeExecutions {
      containsOnly(
        "my/",
        mainClassEntry,
        main2ClassEntry,
        *manifestEntries,
      )
    }
  }

  @Test
  fun dependenciesIncludesExcludesChanged() {
    val mainClassEntry = writeClass(withImports = true)
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
      """.trimIndent() + lineSeparator,
    )

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
          dependencies {
            exclude(dependency('junit:junit'))
          }
        }
      """.trimIndent(),
    )

    assertCompositeExecutions {
      containsOnly(
        "my/",
        mainClassEntry,
        *manifestEntries,
      )
    }
  }
}
