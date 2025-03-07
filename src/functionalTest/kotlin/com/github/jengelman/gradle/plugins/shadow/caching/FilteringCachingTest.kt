package com.github.jengelman.gradle.plugins.shadow.caching

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.doesNotContainEntries
import kotlin.io.path.appendText
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
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

    run(shadowJarTask)

    commonAssertions()

    val replaced = projectScriptPath.readText()
      .replace("exclude(dependency('my:d:1.0'))", "exclude(dependency('my:c:1.0'))")
    projectScriptPath.writeText(replaced)
    val result = run(shadowJarTask)

    assertThat(result).taskOutcomeEquals(shadowJarTask, SUCCESS)
    assertThat(outputShadowJar).useAll {
      val entries = entriesInAB + "d.properties"
      containsEntries(*entries)
      doesNotContainEntries(
        "c.properties",
      )
    }
  }

  @Test
  fun projectExclusionsAffectUpToDateCheck() {
    dependOnAndExcludeArtifactD()

    run(shadowJarTask)

    commonAssertions()

    val replaced = projectScriptPath.readText()
      .replace("exclude(dependency('my:d:1.0'))", "exclude 'a.properties'")
    projectScriptPath.writeText(replaced)

    val result = run(shadowJarTask)

    assertThat(result).taskOutcomeEquals(shadowJarTask, SUCCESS)
    assertThat(outputShadowJar).useAll {
      containsEntries(
        "a2.properties",
        "b.properties",
        "c.properties",
        "d.properties",
      )
      doesNotContainEntries(
        "a.properties",
      )
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/717",
  )
  @Test
  fun shadowJarIsCachedCorrectlyWhenUsingIncludesExcludes() {
    val mainClass = writeClass(className = "Main")
    val main2Class = writeClass(className = "Main2")
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'my:a:1.0'
          implementation 'my:b:1.0'
        }
      """.trimIndent() + System.lineSeparator(),
    )

    assertCompositeExecutions {
      val entries = entriesInAB + arrayOf(mainClass, main2Class)
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
        mainClass,
        main2Class,
      )
      doesNotContainEntries(*entriesInAB)
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
          include '$mainClass'
        }
      """.trimIndent() + System.lineSeparator(),
    )

    assertCompositeExecutions {
      containsEntries(
        mainClass,
      )
      doesNotContainEntries(
        main2Class,
        "a.properties",
        "a2.properties",
        "b.properties",
      )
    }

    projectScriptPath.appendText(
      """
        $shadowJar {
          include '$main2Class'
        }
      """.trimIndent() + System.lineSeparator(),
    )

    assertCompositeExecutions {
      containsEntries(
        mainClass,
        main2Class,
      )
      doesNotContainEntries(*entriesInAB)
    }
  }

  @Test
  fun shadowJarIsCachedCorrectlyWhenUsingDependencyIncludesExcludes() {
    val mainClass = writeClass(withImports = true)
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'junit:junit:3.8.2'
        }
      """.trimIndent() + System.lineSeparator(),
    )

    assertCompositeExecutions {
      containsEntries(
        mainClass,
        *junitEntries,
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
        mainClass,
      )
      doesNotContainEntries(
        *junitEntries,
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

  private fun commonAssertions() {
    assertThat(outputShadowJar).useAll {
      val entries = entriesInAB + "c.properties"
      containsEntries(*entries)
      doesNotContainEntries(
        "d.properties",
      )
    }
  }
}
