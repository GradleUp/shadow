package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.doesNotContainEntries
import kotlin.io.path.appendText
import kotlin.io.path.writeText
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
      doesNotContainEntries(
        *entriesInAB,
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
      containsEntries(
        *entriesInAB,
        "c.properties",
      )
      doesNotContainEntries(
        "d.properties",
      )
    }
  }
}
