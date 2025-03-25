package com.github.jengelman.gradle.plugins.shadow.caching

import assertk.Assert
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.JarPath
import com.github.jengelman.gradle.plugins.shadow.util.containsOnly
import kotlin.io.path.appendText
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class FilteringCachingTest : BaseCachingTest() {
  @BeforeAll
  override fun doFirst() {
    super.doFirst()
    publishArtifactCD()
  }

  @Test
  fun dependencyExclusionsAffectUpToDateCheck() {
    dependOnAndExcludeArtifactD()

    assertCompositeExecutions {
      commonAssertions()
    }

    val replaced = projectScriptPath.readText()
      .replace("exclude(dependency('my:d:1.0'))", "exclude(dependency('my:c:1.0'))")
    projectScriptPath.writeText(replaced)

    assertCompositeExecutions {
      containsOnly(
        "d.properties",
        *entriesInAB,
        *manifestEntries,
      )
    }
  }

  @Test
  fun projectExclusionsAffectUpToDateCheck() {
    dependOnAndExcludeArtifactD()

    assertCompositeExecutions {
      commonAssertions()
    }

    val replaced = projectScriptPath.readText()
      .replace("exclude(dependency('my:d:1.0'))", "exclude 'a.properties'")
    projectScriptPath.writeText(replaced)

    assertCompositeExecutions {
      containsOnly(
        "a2.properties",
        "b.properties",
        "c.properties",
        "d.properties",
        *manifestEntries,
      )
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/717",
  )
  @Test
  fun shadowJarIsCachedCorrectlyWhenUsingIncludesExcludes() {
    val mainClassEntry = writeClass(className = "Main")
    val main2ClassEntry = writeClass(className = "Main2")
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'my:a:1.0'
          implementation 'my:b:1.0'
        }
      """.trimIndent() + System.lineSeparator(),
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
      """.trimIndent() + System.lineSeparator(),
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
      """.trimIndent() + System.lineSeparator(),
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
      """.trimIndent() + System.lineSeparator(),
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
  fun shadowJarIsCachedCorrectlyWhenUsingDependencyIncludesExcludes() {
    val mainClassEntry = writeClass(withImports = true)
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
      """.trimIndent() + System.lineSeparator(),
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

  private fun dependOnAndExcludeArtifactD() {
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'my:a:1.0'
          implementation 'my:b:1.0'
          implementation 'my:d:1.0'
        }
        $shadowJar {
          dependencies {
            exclude(dependency('my:d:1.0'))
          }
        }
      """.trimIndent(),
    )
  }

  private fun Assert<JarPath>.commonAssertions() {
    containsOnly(
      "c.properties",
      *entriesInAB,
      *manifestEntries,
    )
  }
}
