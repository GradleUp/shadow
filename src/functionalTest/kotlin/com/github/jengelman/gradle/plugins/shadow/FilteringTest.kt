package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.SHADOW_JAR_TASK_NAME
import com.github.jengelman.gradle.plugins.shadow.testkit.containsAtLeast
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
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

  @Issue("https://github.com/GradleUp/shadow/issues/882")
  @Test
  fun shadowJarWithArtifactTransformOnProjectDependency() {
    settingsScript.appendText(
      """
      include 'lib', 'app'
      """
        .trimIndent()
    )
    projectScript.writeText("")

    path("lib/src/main/java/lib/Lib.java")
      .writeText(
        """
        package lib;
        public class Lib {}
        """
          .trimIndent()
      )
    path("lib/build.gradle").writeText(getDefaultProjectBuildScript("java") + lineSeparator)

    path("app/src/main/java/app/App.java")
      .writeText(
        """
        package app;
        public class App {}
        """
          .trimIndent()
      )
    path("app/build.gradle")
      .writeText(
        """
        ${getDefaultProjectBuildScript("java")}

        abstract class NoOpTransform implements TransformAction<TransformParameters.None> {
          @InputArtifact
          abstract Provider<FileSystemLocation> getInputArtifact()

          void transform(TransformOutputs outputs) {
            def input = inputArtifact.get().asFile
            def output = outputs.file(input.name)
            output.bytes = input.bytes
          }
        }

        def customAttr = Attribute.of('custom-transformed', Boolean)

        dependencies {
          implementation project(':lib')
          attributesSchema {
            attribute(customAttr)
          }
          artifactTypes.getByName('jar') {
            attributes.attribute(customAttr, false)
          }
          registerTransform(NoOpTransform) {
            from.attribute(customAttr, false)
            to.attribute(customAttr, true)
          }
        }

        configurations.runtimeClasspath {
          attributes.attribute(customAttr, true)
        }
        """
          .trimIndent() + lineSeparator
      )

    runWithSuccess(":app:$SHADOW_JAR_TASK_NAME")

    assertThat(jarPath("app/build/libs/app-1.0-all.jar")).useAll {
      containsAtLeast("app/", "app/App.class", *manifestEntries)
    }
  }

  private fun commonAssertions() {
    assertThat(outputShadowedJar).useAll {
      containsOnly("c.properties", *entriesInAB, *manifestEntries)
    }
  }
}
