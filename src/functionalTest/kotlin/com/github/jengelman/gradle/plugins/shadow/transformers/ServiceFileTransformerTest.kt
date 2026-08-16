package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsMatch
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.testkit.runTest
import com.github.jengelman.gradle.plugins.shadow.testkit.runTests
import de.infix.testBalloon.framework.core.testSuite
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.DuplicatesStrategy.EXCLUDE
import org.gradle.api.file.DuplicatesStrategy.FAIL
import org.gradle.api.file.DuplicatesStrategy.INCLUDE
import org.gradle.api.file.DuplicatesStrategy.INHERIT
import org.gradle.api.file.DuplicatesStrategy.WARN

val ServiceFileTransformerTests by testSuite {
  runTests(::ServiceFileTransformerTest)

  for ((strategy, outputRegex) in ServiceFileTransformerTest.withThrowingProvider) {
    runTest("honorDuplicatesStrategyWithThrowing_$strategy", ::ServiceFileTransformerTest) {
      honorDuplicatesStrategyWithThrowing(strategy, outputRegex)
    }
  }

  for ((strategy, firstValue, secondValue) in ServiceFileTransformerTest.withoutThrowingProvider) {
    runTest("honorDuplicatesStrategyWithoutThrowing_$strategy", ::ServiceFileTransformerTest) {
      honorDuplicatesStrategyWithoutThrowing(strategy, firstValue, secondValue)
    }
  }

  for ((default, override, matchPath) in ServiceFileTransformerTest.eachFileStrategyProvider) {
    runTest(
      "strategyCanBeOverriddenByEachFile_${default}_${override}",
      ::ServiceFileTransformerTest,
    ) {
      strategyCanBeOverriddenByEachFile(default, override, matchPath)
    }
  }
}

private class ServiceFileTransformerTest : BaseTransformerTest() {
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
          """
            .trimMargin()
        )
    }
  }

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
  }

  // #70, #71
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
    assertThat(content).isEqualTo("$CONTENT_THREE\n$CONTENT_ONE_TWO")
  }

  fun honorDuplicatesStrategyWithThrowing(strategy: DuplicatesStrategy, outputRegex: String) {
    writeDuplicatesStrategy(strategy)

    val result = runWithFailure(shadowJarPath)

    assertThat(result.output).containsMatch(outputRegex.toRegex())
  }

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
      getContent(ENTRY_SERVICES_SHADE).isEqualTo(firstValue)
      getContent(ENTRY_SERVICES_FOO).isEqualTo(secondValue)
    }
  }

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
      getContent(ENTRY_SERVICES_SHADE).isEqualTo(CONTENT_ONE_TWO)
      getContent(ENTRY_SERVICES_FOO).isEqualTo("one")
    }
  }

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
      getContent(ENTRY_SERVICES_SHADE).isEqualTo(CONTENT_ONE_TWO)
      getContent(ENTRY_SERVICES_FOO).isEqualTo("one")
    }
  }

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
      getContent(ENTRY_SERVICES_SHADE).isEqualTo(CONTENT_ONE_TWO)
      getContent(ENTRY_SERVICES_FOO).isEqualTo("one")
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

  companion object {
    val withThrowingProvider =
      listOf(
        FAIL to "Cannot copy zip entry .* to .* because zip entry .* has already been copied there",
        INHERIT to "Entry .* is a duplicate but no duplicate handling strategy has been set",
      )

    val withoutThrowingProvider =
      listOf(
        Triple(EXCLUDE, CONTENT_ONE, "one"),
        Triple(INCLUDE, CONTENT_ONE_TWO, "one\ntwo"),
        Triple(WARN, CONTENT_ONE_TWO, "one\ntwo"),
      )

    val eachFileStrategyProvider =
      listOf(
        Triple(EXCLUDE, INCLUDE, ENTRY_SERVICES_SHADE),
        Triple(INCLUDE, EXCLUDE, ENTRY_SERVICES_FOO),
      )
  }
}
