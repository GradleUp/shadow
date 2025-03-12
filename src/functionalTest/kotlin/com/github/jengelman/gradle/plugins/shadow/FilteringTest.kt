package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.util.containsAtLeast
import com.github.jengelman.gradle.plugins.shadow.util.containsNone
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

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
      containsAtLeast(*entriesInAB)
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
      containsAtLeast(
        "a.properties",
        "b.properties",
      )
      containsNone(
        "a2.properties",
      )
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun excludeDependency(useAccessor: Boolean) {
    dependOnAndExcludeArtifactD(useAccessor)

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
      containsAtLeast(
        "d.properties",
        "my/Passed.class",
      )
      containsNone(
        *entriesInAB,
        "c.properties",
      )
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun filterProjectDependencies(useAccessor: Boolean) {
    val clientProject = if (useAccessor) "project(projects.client)" else "project(':client')"
    writeClientAndServerModules(
      serverShadowBlock = """
        dependencies {
          exclude($clientProject)
        }
      """.trimIndent(),
    )

    run(serverShadowJarTask)

    assertThat(outputServerShadowJar).useAll {
      containsAtLeast(
        "server/Server.class",
        *junitEntries,
      )
      containsNone(
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
      containsAtLeast(
        "client/Client.class",
        "server/Server.class",
      )
      containsNone(
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
      containsAtLeast(
        "a.properties",
        "b.properties",
      )
      containsNone(
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

  private fun dependOnAndExcludeArtifactD(useAccessor: Boolean = false) {
    settingsScriptPath.appendText(
      """
        dependencyResolutionManagement {
          versionCatalogs.create('libs') {
            library('my-d', 'my:d:1.0')
          }
        }
      """.trimIndent(),
    )
    val dependency = if (useAccessor) "libs.my.d" else "'my:d:1.0'"
    projectScriptPath.appendText(
      """
        dependencies {
          implementation $dependency
        }
        $shadowJar {
          dependencies {
            exclude(dependency($dependency))
          }
        }
      """.trimIndent(),
    )
  }

  private fun commonAssertions() {
    assertThat(outputShadowJar).useAll {
      containsAtLeast(
        *entriesInAB,
        "c.properties",
      )
      containsNone(
        "d.properties",
      )
    }
  }
}
