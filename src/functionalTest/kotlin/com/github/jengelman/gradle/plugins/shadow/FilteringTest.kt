package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.testkit.classLoader
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.loadClass
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
      |dependencies {
      |  implementation 'my:a:1.0'
      |  implementation 'my:b:1.0'
      |}
      |"""
        .trimMargin()
    )
  }

  @Test
  fun includeAllDependencies() {
    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(*entriesInAB, "META-INF/", "META-INF/MANIFEST.MF")
    }
  }

  @Test
  fun excludeFiles() {
    projectScript.appendText(
      """
      |$shadowJarTask {
      |  exclude 'a2.properties'
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly("a.properties", "b.properties", "META-INF/", "META-INF/MANIFEST.MF")
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun excludeDependency(useAccessor: Boolean) {
    settingsScript.appendText(
      """
      |dependencyResolutionManagement {
      |  versionCatalogs.create('libs') {
      |    library('my-d', 'my:d:1.0')
      |  }
      |}
      """
        .trimMargin()
    )
    val dependency = if (useAccessor) "libs.my.d" else "'my:d:1.0'"
    projectScript.appendText(
      """
      |dependencies {
      |  implementation $dependency
      |}
      |$shadowJarTask {
      |  dependencies {
      |    exclude(dependency($dependency))
      |  }
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    commonAssertions()
  }

  @Test
  fun includeDependencyAndExcludeOthers() {
    projectScript.appendText(
      """
      |dependencies {
      |  implementation 'my:d:1.0'
      |}
      |$shadowJarTask {
      |  dependencies {
      |    include(dependency('my:d:1.0'))
      |  }
      |}
      """
        .trimMargin()
    )
    path("src/main/java/my/Passed.java")
      .writeText(
        """
        |package my;
        |public class Passed {}
        """
          .trimMargin()
      )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "d.properties",
        "my/",
        "my/Passed.class",
        "META-INF/",
        "META-INF/MANIFEST.MF",
      )
      classLoader {
        loadClass("my.Passed")
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun filterProjectDependencies(useAccessor: Boolean) {
    val clientProject = if (useAccessor) "project(projects.client)" else "project(':client')"
    writeClientAndServerModules(
      serverShadowBlock =
        """
        |dependencies {
        |  exclude($clientProject)
        |}
        """
          .trimMargin()
    )

    runWithSuccess(serverShadowJarPath)

    commonServerAssertions()
  }

  @Test // #671
  fun filterProjectThatVersionContainsPlus() {
    writeClientAndServerModules(
      serverShadowBlock =
        """
        |dependencies {
        |  exclude(project(':client'))
        |}
        """
          .trimMargin()
    )
    path("client/build.gradle").appendText("version = '1.0.0+1'")

    runWithSuccess(serverShadowJarPath)

    commonServerAssertions()
  }

  @Test
  fun excludeTransitiveProjectDependency() {
    writeClientAndServerModules(
      serverShadowBlock =
        """
        |dependencies {
        |  exclude { it.moduleGroup == 'junit' }
        |}
        """
          .trimMargin()
    )

    runWithSuccess(serverShadowJarPath)

    assertThat(outputServerShadowedJar).useAll {
      containsOnly(
        "client/",
        "server/",
        "client/Client.class",
        "server/Server.class",
        "META-INF/",
        "META-INF/MANIFEST.MF",
      )
      classLoader {
        loadClass("client.Client")
        loadClass("server.Server")
      }
    }
  }

  @Test
  fun verifyExcludePrecedenceOverInclude() {
    projectScript.appendText(
      """
      |$shadowJarTask {
      |  include '*.jar'
      |  include '*.properties'
      |  exclude 'a2.properties'
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly("a.properties", "b.properties", "META-INF/", "META-INF/MANIFEST.MF")
    }
  }

  @Test
  fun handleExcludeWithCircularDependency() {
    val dependency = "'my:e:1.0'"
    projectScript.appendText(
      """
      |dependencies {
      |  implementation $dependency
      |}
      |$shadowJarTask {
      |  dependencies {
      |    exclude(dependency($dependency))
      |  }
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly("f.properties", *entriesInAB, "META-INF/", "META-INF/MANIFEST.MF")
    }
  }

  private fun commonAssertions() {
    assertThat(outputShadowedJar).useAll {
      containsOnly("c.properties", *entriesInAB, "META-INF/", "META-INF/MANIFEST.MF")
    }
  }

  private fun commonServerAssertions() {
    assertThat(outputServerShadowedJar).useAll {
      containsOnly(
        "server/",
        "server/Server.class",
        *junitEntries,
        "META-INF/",
        "META-INF/MANIFEST.MF",
      )
      classLoader {
        loadClass("server.Server")
        loadClass("junit.framework.Test")
      }
    }
  }
}
