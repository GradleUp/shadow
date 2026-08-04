package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import kotlin.io.path.appendText
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.writeText
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.Parameter
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ValueSource

private val lineSeparator = System.lineSeparator()

@ParameterizedClass
@ValueSource(strings = ["java", "java-library"])
class ServiceFileTransformerCompositeTest : BaseTransformerTest() {

  @Parameter lateinit var javaPlugin: String

  @Test
  fun serviceFileMergingInCompositeBuild() {
    val one = buildJarOne()
    val two = buildJarTwo()

    settingsScript.appendText("includeBuild('included-project')$lineSeparator")

    val includedProjectDir = path("included-project/")
    path("settings.gradle", parent = includedProjectDir)
      .writeText("rootProject.name = 'included-project'$lineSeparator")
    path("build.gradle", parent = includedProjectDir)
      .writeText(
        """
        plugins {
          id '$javaPlugin'
          id 'com.gradleup.shadow'
        }
        group = 'com.example'
        version = '1.0'

        dependencies {
          implementation files('${one.invariantSeparatorsPathString}', '${two.invariantSeparatorsPathString}')
        }

        shadowJar {
          duplicatesStrategy = DuplicatesStrategy.EXCLUDE
          mergeServiceFiles()
          filesMatching('META-INF/services/**') {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
          }
        }
      """
          .trimIndent() + lineSeparator
      )

    projectScript.writeText(
      """
      plugins {
        id '$javaPlugin'
        id 'com.gradleup.shadow'
      }
      group = 'com.example'
      version = '1.0'

      dependencies {
        implementation 'com.example:included-project:1.0'
      }

      shadowJar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        mergeServiceFiles()
        filesMatching('META-INF/services/**') {
          duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
      }
      """
        .trimIndent() + lineSeparator
    )

    runWithSuccess(":included-project:shadowJar", shadowJarPath)

    val includedShadowedJar = jarPath("included-project/build/libs/included-project-1.0-all.jar")
    assertThat(includedShadowedJar).useAll {
      getContent(ENTRY_SERVICES_FOO).isEqualTo("one\ntwo")
    }

    assertThat(outputShadowedJar).useAll {
      getContent(ENTRY_SERVICES_FOO).isEqualTo("one\ntwo")
    }
  }

  @Test
  fun serviceFileMergingInCompositeBuild_whenRootNotConfigured() {
    val one = buildJarOne()
    val two = buildJarTwo()

    settingsScript.appendText("includeBuild('included-project')$lineSeparator")

    val includedProjectDir = path("included-project/")
    path("settings.gradle", parent = includedProjectDir)
      .writeText("rootProject.name = 'included-project'$lineSeparator")
    path("build.gradle", parent = includedProjectDir)
      .writeText(
        """
        plugins {
          id '$javaPlugin'
          id 'com.gradleup.shadow'
        }
        group = 'com.example'
        version = '1.0'

        dependencies {
          implementation files('${one.invariantSeparatorsPathString}', '${two.invariantSeparatorsPathString}')
        }

        shadowJar {
          duplicatesStrategy = DuplicatesStrategy.EXCLUDE
          mergeServiceFiles()
          filesMatching('META-INF/services/**') {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
          }
        }
      """
          .trimIndent() + lineSeparator
      )

    projectScript.writeText(
      """
      plugins {
        id '$javaPlugin'
        id 'com.gradleup.shadow'
      }
      group = 'com.example'
      version = '1.0'

      dependencies {
        implementation 'com.example:included-project:1.0'
      }

      shadowJar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
      }
      """
        .trimIndent() + lineSeparator
    )

    runWithSuccess(":included-project:shadowJar", shadowJarPath)

    val includedShadowedJar = jarPath("included-project/build/libs/included-project-1.0-all.jar")
    assertThat(includedShadowedJar).useAll {
      getContent(ENTRY_SERVICES_FOO).isEqualTo("one\ntwo")
    }

    assertThat(outputShadowedJar).useAll {
      getContent(ENTRY_SERVICES_FOO).isEqualTo("one")
    }
  }

  @Test
  fun serviceFileMergingInCompositeBuild_whenIncludedProjectPublishesShadowJar() {
    val one = buildJarOne()
    val two = buildJarTwo()

    settingsScript.appendText("includeBuild('included-project')$lineSeparator")

    val includedProjectDir = path("included-project/")
    path("settings.gradle", parent = includedProjectDir)
      .writeText("rootProject.name = 'included-project'$lineSeparator")
    path("build.gradle", parent = includedProjectDir)
      .writeText(
        """
        plugins {
          id '$javaPlugin'
          id 'com.gradleup.shadow'
        }
        group = 'com.example'
        version = '1.0'

        dependencies {
          implementation files('${one.invariantSeparatorsPathString}', '${two.invariantSeparatorsPathString}')
        }

        shadowJar {
          duplicatesStrategy = DuplicatesStrategy.EXCLUDE
          mergeServiceFiles()
          filesMatching('META-INF/services/**') {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
          }
        }

        configurations {
          named('apiElements') {
            outgoing.artifacts.clear()
            outgoing.variants.clear()
            outgoing.artifact(tasks.named('shadowJar'))
          }
          named('runtimeElements') {
            outgoing.artifacts.clear()
            outgoing.variants.clear()
            outgoing.artifact(tasks.named('shadowJar'))
          }
        }
      """
          .trimIndent() + lineSeparator
      )

    projectScript.writeText(
      """
      plugins {
        id '$javaPlugin'
        id 'com.gradleup.shadow'
      }
      group = 'com.example'
      version = '1.0'

      dependencies {
        implementation 'com.example:included-project:1.0'
      }

      shadowJar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        mergeServiceFiles()
        filesMatching('META-INF/services/**') {
          duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
      }
      """
        .trimIndent() + lineSeparator
    )

    runWithSuccess(shadowJarPath)

    val includedShadowedJar = jarPath("included-project/build/libs/included-project-1.0-all.jar")
    assertThat(includedShadowedJar).useAll {
      getContent(ENTRY_SERVICES_FOO).isEqualTo("one\ntwo")
    }

    assertThat(outputShadowedJar).useAll {
      getContent(ENTRY_SERVICES_FOO).isEqualTo("one\ntwo")
    }
  }
}
