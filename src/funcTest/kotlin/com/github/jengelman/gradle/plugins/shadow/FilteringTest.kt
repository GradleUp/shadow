package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.doesNotContainEntries
import com.github.jengelman.gradle.plugins.shadow.util.useAll
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
          implementation 'shadow:a:1.0'
          implementation 'shadow:b:1.0'
        }
      """.trimIndent() + System.lineSeparator(),
    )
  }

  @Test
  fun includeAllDependencies() {
    run(shadowJarTask)
    assertThat(outputShadowJar).useAll {
      containsEntries(
        "a.properties",
        "a2.properties",
        "b.properties",
      )
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
          implementation 'shadow:d:1.0'
        }
        $shadowJar {
          dependencies {
            exclude(dependency('shadow:d:.*'))
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
      .replace("exclude(dependency('shadow:d:1.0'))", "exclude(dependency('shadow:c:1.0'))")
    projectScriptPath.writeText(replaced)
    val result = run(shadowJarTask)

    assertThat(result).taskOutcomeEquals(shadowJarTask, SUCCESS)
    assertThat(outputShadowJar).useAll {
      containsEntries(
        "a.properties",
        "a2.properties",
        "b.properties",
        "d.properties",
      )
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
      .replace("exclude(dependency('shadow:d:1.0'))", "exclude 'a.properties'")
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
          implementation 'shadow:d:1.0'
        }
        $shadowJar {
          dependencies {
            include(dependency('shadow:d:1.0'))
          }
        }
      """.trimIndent(),
    )
    path("src/main/java/shadow/Passed.java").writeText(
      """
        package shadow;
        public class Passed {}
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsEntries(
        "d.properties",
        "shadow/Passed.class",
      )
      doesNotContainEntries(
        "a.properties",
        "a2.properties",
        "b.properties",
        "c.properties",
      )
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
      doesNotContainEntries(
        "client/Client.class",
      )
      containsEntries(
        "server/Server.class",
        "junit/framework/Test.class",
      )
    }
  }

  @Test
  fun excludeATransitiveProjectDependency() {
    writeClientAndServerModules(
      serverShadowBlock = """
        dependencies {
          exclude(dependency { it.moduleGroup == 'junit' })
        }
      """.trimIndent(),
    )

    run(serverShadowJarTask)

    assertThat(outputServerShadowJar).useAll {
      doesNotContainEntries(
        "junit/framework/Test.class",
      )
      containsEntries(
        "client/Client.class",
        "server/Server.class",
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
          implementation 'shadow:d:1.0'
        }
        $shadowJar {
          dependencies {
            exclude(dependency('shadow:d:1.0'))
          }
        }
      """.trimIndent(),
    )
  }

  private fun commonAssertions() {
    assertThat(outputShadowJar).useAll {
      containsEntries(
        "a.properties",
        "a2.properties",
        "b.properties",
        "c.properties",
      )
      doesNotContainEntries(
        "d.properties",
      )
    }
  }

  private fun publishArtifactCD(circular: Boolean = false) {
    localRepo.module("shadow", "c", "1.0") {
      buildJar {
        insert("c.properties", "c")
      }
      if (circular) {
        addDependency("shadow", "d", "1.0")
      }
    }.module("shadow", "d", "1.0") {
      buildJar {
        insert("d.properties", "d")
      }
      addDependency("shadow", "c", "1.0")
    }.publish()
  }
}
