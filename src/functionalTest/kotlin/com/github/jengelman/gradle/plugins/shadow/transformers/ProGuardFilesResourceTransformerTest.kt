package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.util.classLoader
import kotlin.io.path.appendText
import org.junit.jupiter.api.Test

class ProGuardFilesResourceTransformerTest : BaseTransformerTest() {
  @Test
  fun mergeProguardFilesSameAndDifferentNames() {
    val one = buildJarOne {
      insert("META-INF/proguard/rules.pro", "-keep class com.example.Client")
      insert("META-INF/proguard/client.pro", "-dontwarn com.example.client.**")
    }
    val two = buildJarTwo {
      insert("META-INF/proguard/rules.pro", "-keep class com.example.Server")
      insert("META-INF/proguard/server.pro", "-dontwarn com.example.server.**")
    }

    projectScript.appendText(
      """
      |dependencies {
      |  ${implementationFiles(one, two)}
      |}
      |$shadowJarTask {
      |  transform(${ProGuardFilesResourceTransformer::class.java.name})
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "META-INF/proguard/",
        "META-INF/proguard/rules.pro",
        "META-INF/proguard/client.pro",
        "META-INF/proguard/server.pro",
        *manifestEntries,
      )
      getContent("META-INF/proguard/rules.pro")
        .isEqualTo(
          """
          |-keep class com.example.Client
          |-keep class com.example.Server
          """
            .trimMargin()
        )
      getContent("META-INF/proguard/client.pro").isEqualTo("-dontwarn com.example.client.**")
      getContent("META-INF/proguard/server.pro").isEqualTo("-dontwarn com.example.server.**")
    }
  }

  @Test
  fun mergeProguardFilesWithRelocation() {
    val one = buildJarOne {
      insert("com/example/Driver.class", createEmptyClassBytes("com/example/Driver"))
      insert("foo/FooDriver.class", createEmptyClassBytes("foo/FooDriver"))
      insert(
        "META-INF/proguard/rules.pro",
        """
        |# Core rules
        |-keep class com.example.Driver { *; }
        |-dontwarn foo.**
        |-keep class foo.?Driver
        """
          .trimMargin(),
      )
    }
    val two = buildJarTwo {
      insert("bar/BarDriver.class", createEmptyClassBytes("bar/BarDriver"))
      insert(
        "META-INF/proguard/rules.pro",
        """
        |# Extension rules
        |-keep class bar.BarDriver
        """
          .trimMargin(),
      )
    }

    projectScript.appendText(
      """
      |dependencies {
      |  ${implementationFiles(one, two)}
      |}
      |$shadowJarTask {
      |  transform(${ProGuardFilesResourceTransformer::class.java.name})
      |  relocate("com.example", "relocated.com.example")
      |  relocate("foo", "relocated.foo")
      |  relocate("bar", "relocated.bar")
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "relocated/",
        "relocated/bar/",
        "relocated/bar/BarDriver.class",
        "relocated/com/",
        "relocated/com/example/",
        "relocated/com/example/Driver.class",
        "relocated/foo/",
        "relocated/foo/FooDriver.class",
        "META-INF/proguard/",
        "META-INF/proguard/rules.pro",
        *manifestEntries,
      )
      getContent("META-INF/proguard/rules.pro")
        .isEqualTo(
          """
          |# Core rules
          |-keep class relocated.com.example.Driver { *; }
          |-dontwarn relocated.foo.**
          |-keep class relocated.foo.?Driver
          |# Extension rules
          |-keep class relocated.bar.BarDriver
          """
            .trimMargin()
        )
    }
    outputShadowedJar.classLoader().use { classLoader ->
      val driver = classLoader.loadClass("relocated.com.example.Driver")
      val fooDriver = classLoader.loadClass("relocated.foo.FooDriver")
      val barDriver = classLoader.loadClass("relocated.bar.BarDriver")
      assertThat(driver.name).isEqualTo("relocated.com.example.Driver")
      assertThat(fooDriver.name).isEqualTo("relocated.foo.FooDriver")
      assertThat(barDriver.name).isEqualTo("relocated.bar.BarDriver")
    }
  }
}
