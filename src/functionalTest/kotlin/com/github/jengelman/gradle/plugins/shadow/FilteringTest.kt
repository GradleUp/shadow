package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.util.containsOnly
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
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
      """.trimIndent() + lineSeparator,
    )
  }

  @Test
  fun includeAllDependencies() {
    run(shadowJarPath)

    assertThat(outputShadowJar).useAll {
      containsOnly(
        *entriesInAB,
        *manifestEntries,
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

    run(shadowJarPath)

    assertThat(outputShadowJar).useAll {
      containsOnly(
        "a.properties",
        "b.properties",
        *manifestEntries,
      )
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun excludeDependency(useAccessor: Boolean) {
    dependOnAndExcludeArtifactD(useAccessor)

    run(shadowJarPath)

    commonAssertions()
  }

  @ParameterizedTest
  @MethodSource("wildcardDepProvider")
  fun excludeDependencyUsingWildcardSyntax(wildcard: String) {
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'my:d:1.0'
        }
        $shadowJar {
          dependencies {
            exclude(dependency('$wildcard'))
          }
        }
      """.trimIndent(),
    )

    run(shadowJarPath)

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

    run(shadowJarPath)

    assertThat(outputShadowJar).useAll {
      containsOnly(
        "d.properties",
        "my/",
        "my/Passed.class",
        *manifestEntries,
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

    run(serverShadowJarPath)

    assertThat(outputServerShadowJar).useAll {
      containsOnly(
        "server/",
        "server/Server.class",
        *junitEntries,
        *manifestEntries,
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

    run(serverShadowJarPath)

    assertThat(outputServerShadowJar).useAll {
      containsOnly(
        "client/",
        "server/",
        "client/Client.class",
        "server/Server.class",
        *manifestEntries,
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

    run(shadowJarPath)

    assertThat(outputShadowJar).useAll {
      containsOnly(
        "a.properties",
        "b.properties",
        *manifestEntries,
      )
    }
  }

  @Test
  fun handleExcludeWithCircularDependency() {
    publishArtifactCD(circular = true)
    dependOnAndExcludeArtifactD()

    run(shadowJarPath)

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
      containsOnly(
        "c.properties",
        *entriesInAB,
        *manifestEntries,
      )
    }
  }

  private companion object {
    @JvmStatic
    fun wildcardDepProvider() = listOf(
      "my:d",
      "m.*:d",
      "my:d:.*",
      "m.*:d:.*",
      "m.*:d.*:.*",
      ".*:d:.*",
    )
  }
}
