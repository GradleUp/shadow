package com.github.jengelman.gradle.plugins.shadow

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.testkit.classLoader
import com.github.jengelman.gradle.plugins.shadow.testkit.containsExactly
import com.github.jengelman.gradle.plugins.shadow.testkit.loadClass
import kotlin.io.path.appendText
import kotlin.io.path.readBytes
import org.junit.jupiter.api.Test

class ParallelRelocationTest : BasePluginTest() {
  @Test
  fun largeNumberOfClassesWithRelocation() {
    val count = 500
    val classNames = (1..count).map { "Class%03d".format(it) }
    val largeJar =
      buildJar("many-classes.jar") {
        for (name in classNames) {
          insert(
            "com/example/pkg/$name.class",
            createEmptyClassBytes("com/example/pkg/$name"),
          )
        }
      }

    projectScript.appendText(
      """
      |dependencies {
      |  ${implementationFiles(largeJar)}
      |}
      |$shadowJarTask {
      |  relocate 'com.example.pkg', 'relocated.pkg'
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      val relocatedEntries = classNames.map { "relocated/pkg/$it.class" }.toTypedArray()
      containsExactly(
        "META-INF/MANIFEST.MF",
        *relocatedEntries,
        "META-INF/",
        "relocated/pkg/",
        "relocated/",
      )
      classLoader {
        loadClass("relocated.pkg.${classNames.first()}")
        loadClass("relocated.pkg.${classNames.last()}")
      }
    }
  }

  @Test
  fun deterministicZipEntryOrderAcrossMultipleBuilds() {
    val count = 150
    val testJar =
      buildJar("deterministic-test.jar") {
        for (i in 1..count) {
          insert(
            "com/example/test/TestClass$i.class",
            createEmptyClassBytes("com/example/test/TestClass$i"),
          )
          insert("resources/res_$i.txt", "content $i")
        }
      }

    projectScript.appendText(
      """
      |dependencies {
      |  ${implementationFiles(testJar)}
      |}
      |$shadowJarTask {
      |  relocate 'com.example.test', 'shadowed.example.test'
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)
    val firstBytes = path("build/libs/my-1.0-all.jar").readBytes()

    runWithSuccess(shadowJarPath, "--rerun-tasks")
    val secondBytes = path("build/libs/my-1.0-all.jar").readBytes()

    assertThat(firstBytes).isEqualTo(secondBytes)
  }

  @Test
  fun errorPropagationWhenClassIsCorrupted() {
    val badClassEntry = "corrupt/BadClass.class"
    val corruptJar =
      buildJar("corrupt.jar") {
        insert(badClassEntry, byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte()))
      }

    projectScript.appendText(
      """
      |dependencies {
      |  ${implementationFiles(corruptJar)}
      |}
      |$shadowJarTask {
      |  relocate 'corrupt', 'relocated.corrupt'
      |}
      """
        .trimMargin()
    )

    val result = runWithFailure(shadowJarPath)

    assertThat(result.output).contains("Error in ASM processing class $badClassEntry")
  }
}
