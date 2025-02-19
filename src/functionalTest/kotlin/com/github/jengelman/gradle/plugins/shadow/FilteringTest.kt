package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.doesNotContainEntries
import kotlin.io.path.appendText
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FilteringTest : BasePluginTest() {
  @BeforeAll
  override fun doFirst() {
    super.doFirst()
    publishArtifactCD()
  }

  @BeforeEach
  override fun setup() {
    super.setup()
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'my:a:1.0'
          implementation 'my:b:1.0'
        }
      """.trimIndent() + System.lineSeparator(),
    )
  }

  @Test
  fun includeAllDependencies() {
    run(shadowJarTask)
    assertThat(outputShadowJar).useAll {
      containsEntries(*entriesInAB)
    }
  }

  @Test
  fun excludeFiles() {
    projectScriptPath.appendText(
      """
        $shadowJar {
          exclude 'a2.properties'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsEntries(
        "a.properties",
        "b.properties",
      )
      doesNotContainEntries(
        "a2.properties",
      )
    }
  }

  @Test
  fun excludeDependency() {
    dependOnAndExcludeArtifactD()

    run(shadowJarTask)

    commonAssertions()
  }

  @Test
  fun excludeDependencyUsingWildcardSyntax() {
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'my:d:1.0'
        }
        $shadowJar {
          dependencies {
            exclude(dependency('my:d:.*'))
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    commonAssertions()
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

  @Test
  fun includeDependencyAndExcludeOthers() {
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'my:d:1.0'
        }
        $shadowJar {
          dependencies {
            include(dependency('my:d:1.0'))
          }
        }
      """.trimIndent(),
    )
    path("src/main/java/my/Passed.java").writeText(
      """
        package my;
        public class Passed {}
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsEntries(
        "d.properties",
        "my/Passed.class",
      )
      val entries = entriesInAB + "c.properties"
      doesNotContainEntries(*entries)
    }
  }

  @Test
  fun filterProjectDependencies() {
    writeClientAndServerModules(
      serverShadowBlock = """
        dependencies {
          exclude(project(':client'))
        }
      """.trimIndent(),
    )

    run(serverShadowJarTask)

    assertThat(outputServerShadowJar).useAll {
      containsEntries(
        "server/Server.class",
        *junitEntries,
      )
      doesNotContainEntries(
        "client/Client.class",
      )
    }
  }

  @Test
  fun excludeATransitiveProjectDependency() {
    writeClientAndServerModules(
      serverShadowBlock = """
        dependencies {
          exclude { it.moduleGroup == 'junit' }
        }
      """.trimIndent(),
    )

    run(serverShadowJarTask)

    assertThat(outputServerShadowJar).useAll {
      containsEntries(
        "client/Client.class",
        "server/Server.class",
      )
      doesNotContainEntries(
        *junitEntries,
      )
    }
  }

  @Test
  fun verifyExcludePrecedenceOverInclude() {
    projectScriptPath.appendText(
      """
        $shadowJar {
          include '*.jar'
          include '*.properties'
          exclude 'a2.properties'
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsEntries(
        "a.properties",
        "b.properties",
      )
      doesNotContainEntries(
        "a2.properties",
      )
    }
  }

  @Test
  fun handleExcludeWithCircularDependency() {
    publishArtifactCD(circular = true)
    dependOnAndExcludeArtifactD()

    run(shadowJarTask)

    commonAssertions()
  }

  private fun dependOnAndExcludeArtifactD() {
    projectScriptPath.appendText(
      """
        dependencies {
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
