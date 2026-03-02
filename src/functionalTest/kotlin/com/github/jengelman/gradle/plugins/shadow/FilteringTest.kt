package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isNull
import com.github.jengelman.gradle.plugins.shadow.internal.classPathAttributeKey
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.getMainAttr
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class FilteringTest : BasePluginTest() {
  @BeforeEach
  override fun beforeEach() {
    super.beforeEach()
    projectScript.appendText(
      """
      dependencies {
        implementation 'my:a:1.0'
        implementation 'my:b:1.0'
      }
      """
        .trimIndent() + lineSeparator
    )
  }

  @Test
  fun includeAllDependencies() {
    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll { containsOnly(*entriesInAB, *manifestEntries) }
  }

  @Test
  fun excludeFiles() {
    projectScript.appendText(
      """
        $shadowJarTask {
          exclude 'a2.properties'
        }
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly("a.properties", "b.properties", *manifestEntries)
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun excludeDependency(useAccessor: Boolean) {
    settingsScript.appendText(
      """
      dependencyResolutionManagement {
        versionCatalogs.create('libs') {
          library('my-d', 'my:d:1.0')
        }
      }
      """
        .trimIndent()
    )
    val dependency = if (useAccessor) "libs.my.d" else "'my:d:1.0'"
    projectScript.appendText(
      """
        dependencies {
          implementation $dependency
        }
        $shadowJarTask {
          dependencies {
            exclude(dependency($dependency))
          }
        }
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    commonAssertions()
  }

  @ParameterizedTest
  @ValueSource(strings = ["my:d", "m.*:d", "my:d:.*", "m.*:d:.*", "m.*:d.*:.*", ".*:d:.*"])
  fun excludeDependencyUsingWildcardSyntax(wildcard: String) {
    projectScript.appendText(
      """
        dependencies {
          implementation 'my:d:1.0'
        }
        $shadowJarTask {
          dependencies {
            exclude(dependency('$wildcard'))
          }
        }
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    commonAssertions()
  }

  @Test
  fun includeDependencyAndExcludeOthers() {
    projectScript.appendText(
      """
        dependencies {
          implementation 'my:d:1.0'
        }
        $shadowJarTask {
          dependencies {
            include(dependency('my:d:1.0'))
          }
        }
      """
        .trimIndent()
    )
    path("src/main/java/my/Passed.java")
      .writeText(
        """
        package my;
        public class Passed {}
        """
          .trimIndent()
      )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly("d.properties", "my/", "my/Passed.class", *manifestEntries)
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun filterProjectDependencies(useAccessor: Boolean) {
    val clientProject = if (useAccessor) "project(projects.client)" else "project(':client')"
    writeClientAndServerModules(
      serverShadowBlock =
        """
        dependencies {
          exclude($clientProject)
        }
      """
          .trimIndent()
    )

    runWithSuccess(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsOnly("server/", "server/Server.class", *junitEntries, *manifestEntries)
    }
  }

  @Issue("https://github.com/GradleUp/shadow/issues/671")
  @Test
  fun filterProjectThatVersionContainsPlus() {
    writeClientAndServerModules(
      serverShadowBlock =
        """
        dependencies {
          exclude(project(':client'))
        }
        """
          .trimIndent()
    )
    path("client/build.gradle").appendText("version = '1.0.0+1'")

    runWithSuccess(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsOnly("server/", "server/Server.class", *junitEntries, *manifestEntries)
    }
  }

  @Test
  fun excludeTransitiveProjectDependency() {
    writeClientAndServerModules(
      serverShadowBlock =
        """
        dependencies {
          exclude { it.moduleGroup == 'junit' }
        }
        """
          .trimIndent()
    )

    runWithSuccess(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
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
    projectScript.appendText(
      """
        $shadowJarTask {
          include '*.jar'
          include '*.properties'
          exclude 'a2.properties'
        }
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly("a.properties", "b.properties", *manifestEntries)
    }
  }

  @Test
  fun handleExcludeWithCircularDependency() {
    val dependency = "'my:e:1.0'"
    projectScript.appendText(
      """
        dependencies {
          implementation $dependency
        }
        $shadowJarTask {
          dependencies {
            exclude(dependency($dependency))
          }
        }
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly("f.properties", *entriesInAB, *manifestEntries)
    }
  }

  @Issue("https://github.com/GradleUp/shadow/issues/265")
  @Test
  fun excludedDependenciesAddedToClassPathInManifest() {
    projectScript.appendText(
      """
        dependencies {
          implementation 'my:d:1.0'
        }
        $shadowJarTask {
          dependencies {
            include(dependency('my:d:1.0'))
          }
        }
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    // Only d is bundled in the shadow jar; a and b (first-level deps not matching the include spec)
    // and c (transitive dep of d that also doesn't match the include spec) are all excluded.
    assertThat(outputShadowedJar).useAll { containsOnly("d.properties", *manifestEntries) }
    // Excluded deps a, b, c should appear in the Class-Path manifest attribute automatically.
    val classPath = outputShadowedJar.use { it.getMainAttr(classPathAttributeKey) }
    assertThat(checkNotNull(classPath)).contains("a-1.0.jar", "b-1.0.jar", "c-1.0.jar")
  }

  @Issue("https://github.com/GradleUp/shadow/issues/265")
  @Test
  fun excludedDependenciesNotAddedToClassPathWhenFlagDisabled() {
    projectScript.appendText(
      """
        shadow {
          addExcludedDependenciesToShadowConfiguration = false
        }
        dependencies {
          implementation 'my:d:1.0'
        }
        $shadowJarTask {
          dependencies {
            include(dependency('my:d:1.0'))
          }
        }
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll { containsOnly("d.properties", *manifestEntries) }
    // Class-Path should NOT contain excluded deps when the flag is disabled.
    val classPath = outputShadowedJar.use { it.getMainAttr(classPathAttributeKey) }
    assertThat(classPath).isNull()
  }

  private fun commonAssertions() {
    assertThat(outputShadowedJar).useAll {
      containsOnly("c.properties", *entriesInAB, *manifestEntries)
    }
  }
}
