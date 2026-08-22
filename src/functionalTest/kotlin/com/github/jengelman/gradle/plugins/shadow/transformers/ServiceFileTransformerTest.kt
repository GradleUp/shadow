package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsMatch
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.util.classLoader
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.DuplicatesStrategy.EXCLUDE
import org.gradle.api.file.DuplicatesStrategy.FAIL
import org.gradle.api.file.DuplicatesStrategy.INCLUDE
import org.gradle.api.file.DuplicatesStrategy.INHERIT
import org.gradle.api.file.DuplicatesStrategy.WARN
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ServiceFileTransformerTest : BaseTransformerTest() {
  @Test
  fun serviceResourceTransformerAlternatePath() {
    val one = buildJarOne { insert(ENTRY_FOO_SHADE, CONTENT_ONE) }
    val two = buildJarTwo { insert(ENTRY_FOO_SHADE, CONTENT_TWO) }
    val config =
      """
      |dependencies {
      |  ${implementationFiles(one, two)}
      |}
      |$shadowJarTask {
      |  mergeServiceFiles("META-INF/foo")
      |}
      """
        .trimMargin()
    projectScript.appendText(config)

    runWithSuccess(shadowJarPath)

    val content = outputShadowedJar.use { it.getContent(ENTRY_FOO_SHADE) }
    assertThat(content).isEqualTo("$CONTENT_ONE_TWO\n")
  }

  @Test
  fun serviceResourceTransformerWithRelocation() {
    val one = buildJarOne {
      insert("com/example/Driver.class", createEmptyClassBytes("com/example/Driver"))
      insert("foo/FooDriver.class", createEmptyClassBytes("foo/FooDriver"))
      insert(
        "META-INF/services/com.example.Driver",
        "foo.FooDriver",
      )
    }
    val two = buildJarTwo {
      insert("bar/BarDriver.class", createEmptyClassBytes("bar/BarDriver"))
      insert(
        "META-INF/services/com.example.Driver",
        "bar.BarDriver",
      )
    }

    projectScript.appendText(
      """
      |dependencies {
      |  ${implementationFiles(one, two)}
      |}
      |$shadowJarTask {
      |  mergeServiceFiles()
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
        "META-INF/services/",
        "META-INF/services/relocated.com.example.Driver",
        *manifestEntries,
      )
      getContent("META-INF/services/relocated.com.example.Driver")
        .isEqualTo(
          """
          |relocated.foo.FooDriver
          |relocated.bar.BarDriver
          |"""
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

  @Test
  fun serviceResourceTransformerWithR8Relocation() {
    val one = buildJarOne {
      insert("com/example/Driver.class", createEmptyClassBytes("com/example/Driver"))
      insert("foo/FooDriver.class", createEmptyClassBytes("foo/FooDriver"))
      insert(
        "META-INF/services/com.example.Driver",
        "foo.FooDriver",
      )
    }
    val two = buildJarTwo {
      insert("bar/BarDriver.class", createEmptyClassBytes("bar/BarDriver"))
      insert(
        "META-INF/services/com.example.Driver",
        "bar.BarDriver",
      )
    }

    writeR8Repository()
    projectScript.appendText(
      """
      |dependencies {
      |  ${implementationFiles(one, two)}
      |}
      |$shadowJarTask {
      |  mergeServiceFiles()
      |  minimize {
      |    r8 {
      |      proguardRules.addAll(
      |        "-repackageclasses 'relocated'",
      |      )
      |    }
      |  }
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
        "META-INF/services/",
        "META-INF/services/relocated.com.example.Driver",
        *manifestEntries,
      )
      getContent("META-INF/services/relocated.com.example.Driver")
        .isEqualTo(
          """
          |relocated.foo.FooDriver
          |relocated.bar.BarDriver
          |"""
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

  @Test // #70, #71
  fun transformProjectResources() {
    val servicesBarEntry = "META-INF/services/foo.Bar"
    val one = buildJarOne { insert(servicesBarEntry, CONTENT_ONE) }
    val two = buildJarTwo { insert(servicesBarEntry, CONTENT_TWO) }
    projectScript.appendText(
      """
      |dependencies {
      |  ${implementationFiles(one, two)}
      |}
      |$shadowJarTask {
      |  mergeServiceFiles()
      |}
      """
        .trimMargin()
    )
    path("src/main/resources/$servicesBarEntry").writeText(CONTENT_THREE)

    runWithSuccess(shadowJarPath)

    val content = outputShadowedJar.use { it.getContent(servicesBarEntry) }
    assertThat(content).isEqualTo("$CONTENT_THREE\n$CONTENT_ONE_TWO\n")
  }

  @ParameterizedTest
  @MethodSource("withThrowingProvider")
  fun honorDuplicatesStrategyWithThrowing(strategy: DuplicatesStrategy, outputRegex: String) {
    writeDuplicatesStrategy(strategy)

    val result = runWithFailure(shadowJarPath)

    assertThat(result.output).containsMatch(outputRegex.toRegex())
  }

  @ParameterizedTest
  @MethodSource("withoutThrowingProvider")
  fun honorDuplicatesStrategyWithoutThrowing(
    strategy: DuplicatesStrategy,
    firstValue: String,
    secondValue: String,
  ) {
    writeDuplicatesStrategy(strategy)

    val result = runWithSuccess(shadowJarPath)

    if (strategy == EXCLUDE) {
      assertThat(result.output)
        .contains(
          "'META-INF/services/com.acme.Foo' is matched by com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer but its DuplicatesStrategy is EXCLUDE — duplicates may be silently dropped before the transformer processes them.",
          "'META-INF/services/org.apache.maven.Shade' is matched by com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer but its DuplicatesStrategy is EXCLUDE — duplicates may be silently dropped before the transformer processes them.",
        )
    }

    assertThat(outputShadowedJar).useAll {
      getContent(ENTRY_SERVICES_SHADE).isEqualTo("$firstValue\n")
      getContent(ENTRY_SERVICES_FOO).isEqualTo("$secondValue\n")
    }
  }

  @Test
  fun strategyCanBeOverriddenByFilesMatching() {
    writeDuplicatesStrategy(EXCLUDE)
    projectScript.appendText(
      """
      |$shadowJarTask {
      |  filesMatching('$ENTRY_SERVICES_SHADE') {
      |    duplicatesStrategy = DuplicatesStrategy.INCLUDE
      |  }
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      getContent(ENTRY_SERVICES_SHADE).isEqualTo("$CONTENT_ONE_TWO\n")
      getContent(ENTRY_SERVICES_FOO).isEqualTo("one\n")
    }
  }

  @Test
  fun strategyCanBeOverriddenByFilesNotMatching() {
    writeDuplicatesStrategy(INCLUDE)
    projectScript.appendText(
      """
      |$shadowJarTask {
      |  filesNotMatching('$ENTRY_SERVICES_SHADE') {
      |    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
      |  }
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      getContent(ENTRY_SERVICES_SHADE).isEqualTo("$CONTENT_ONE_TWO\n")
      getContent(ENTRY_SERVICES_FOO).isEqualTo("one\n")
    }
  }

  @ParameterizedTest
  @MethodSource("eachFileStrategyProvider")
  fun strategyCanBeOverriddenByEachFile(
    default: DuplicatesStrategy,
    override: DuplicatesStrategy,
    matchPath: String,
  ) {
    writeDuplicatesStrategy(default)
    projectScript.appendText(
      """
      |$shadowJarTask {
      |  eachFile {
      |    if (path == '$matchPath') {
      |      duplicatesStrategy = DuplicatesStrategy.$override
      |    }
      |  }
      |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      getContent(ENTRY_SERVICES_SHADE).isEqualTo("$CONTENT_ONE_TWO\n")
      getContent(ENTRY_SERVICES_FOO).isEqualTo("one\n")
    }
  }

  private fun writeDuplicatesStrategy(strategy: DuplicatesStrategy) {
    projectScript.appendText(
      """
      |dependencies {
      |  ${implementationFiles(buildJarOne(), buildJarTwo())}
      |}
      |$shadowJarTask {
      |  duplicatesStrategy = DuplicatesStrategy.$strategy
      |  mergeServiceFiles()
      |}
      |
      """
        .trimMargin()
    )
  }

  private companion object {
    @JvmStatic
    fun withThrowingProvider() =
      listOf(
        Arguments.of(
          FAIL,
          "Cannot copy zip entry .* to .* because zip entry .* has already been copied there",
        ),
        Arguments.of(
          INHERIT,
          "Entry .* is a duplicate but no duplicate handling strategy has been set",
        ),
      )

    @JvmStatic
    fun withoutThrowingProvider() =
      listOf(
        Arguments.of(EXCLUDE, CONTENT_ONE, "one"),
        Arguments.of(INCLUDE, CONTENT_ONE_TWO, "one\ntwo"),
        Arguments.of(WARN, CONTENT_ONE_TWO, "one\ntwo"),
      )

    @JvmStatic
    fun eachFileStrategyProvider() =
      listOf(
        Arguments.of(EXCLUDE, INCLUDE, ENTRY_SERVICES_SHADE),
        Arguments.of(INCLUDE, EXCLUDE, ENTRY_SERVICES_FOO),
      )
  }
}
