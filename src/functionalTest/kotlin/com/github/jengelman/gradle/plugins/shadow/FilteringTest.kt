package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.runTest
import com.github.jengelman.gradle.plugins.shadow.testkit.runTests
import de.infix.testBalloon.framework.core.testSuite
import kotlin.io.path.appendText
import kotlin.io.path.writeText

val FilteringTests by testSuite {
  runTests(::FilteringTest)

  for (useAccessor in listOf(false, true)) {
    runTest("excludeDependency_useAccessor_$useAccessor", ::FilteringTest) {
      excludeDependency(useAccessor)
    }
    runTest("filterProjectDependencies_useAccessor_$useAccessor", ::FilteringTest) {
      filterProjectDependencies(useAccessor)
    }
  }
}

private class FilteringTest : BasePluginTest() {
  init {
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

  fun includeAllDependencies() {
    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll { containsOnly(*entriesInAB, *manifestEntries) }
  }

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
      containsOnly("a.properties", "b.properties", *manifestEntries)
    }
  }

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
      containsOnly("d.properties", "my/", "my/Passed.class", *manifestEntries)
    }
  }

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

    assertThat(outputServerShadowedJar).useAll {
      containsOnly("server/", "server/Server.class", *junitEntries, *manifestEntries)
    }
  }

  // #671
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

    assertThat(outputServerShadowedJar).useAll {
      containsOnly("server/", "server/Server.class", *junitEntries, *manifestEntries)
    }
  }

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
        *manifestEntries,
      )
    }
  }

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
      containsOnly("a.properties", "b.properties", *manifestEntries)
    }
  }

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
      containsOnly("f.properties", *entriesInAB, *manifestEntries)
    }
  }

  private fun commonAssertions() {
    assertThat(outputShadowedJar).useAll {
      containsOnly("c.properties", *entriesInAB, *manifestEntries)
    }
  }
}
