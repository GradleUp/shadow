package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.testkit.containsAtLeast
import com.github.jengelman.gradle.plugins.shadow.testkit.containsExactlyInAnyOrder
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.testkit.getContents
import java.util.jar.Attributes as JarAttribute
import kotlin.io.path.appendText
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.writeText
import kotlin.reflect.KClass
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class TransformersTest : BaseTransformerTest() {

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun deduplicatingResourceTransformer(excludeAll: Boolean) {
    val one = buildJarOne {
      insert("multiple-contents", "content")
      insert("single-source", "content")
      insert("same-content-twice", "content")
      insert("differing-content-2", "content")
    }
    val two = buildJarTwo {
      insert("multiple-contents", "content-is-different")
      insert("same-content-twice", "content")
      insert("differing-content-2", "content-is-different")
    }

    projectScript.appendText(
      transform<DeduplicatingResourceTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock =
          """
          exclude('multiple-contents')
          ${if (excludeAll) "exclude('differing-content-2')" else ""}
        """
            .trimIndent(),
      )
    )

    if (excludeAll) {
      runWithSuccess(shadowJarPath)
      assertThat(outputShadowedJar).useAll {
        containsExactlyInAnyOrder(
          // twice:
          "multiple-contents",
          "multiple-contents",
          "single-source",
          "same-content-twice",
          // twice:
          "differing-content-2",
          "differing-content-2",
          "META-INF/",
          "META-INF/MANIFEST.MF",
        )
        getContents("multiple-contents")
          .containsExactlyInAnyOrder("content", "content-is-different")
        getContent("single-source").isEqualTo("content")
        getContent("same-content-twice").isEqualTo("content")
        getContents("differing-content-2")
          .containsExactlyInAnyOrder("content", "content-is-different")
      }
    } else {
      val buildResult = runWithFailure(shadowJarPath)
      assertThat(buildResult).taskOutcomeEquals(shadowJarPath, FAILED)
      assertThat(buildResult.output)
        .contains(
          // Keep this list approach for Unix/Windows test compatibility.
          "Execution failed for task ':shadowJar'",
          "> Found 1 path duplicate(s) with different content in the shadowed JAR:",
          "    * differing-content-2",
          "differing-content-2 (SHA256: ed7002b439e9ac845f22357d822bac1444730fbdb6016d3ec9432297b9ec9f73)",
          "differing-content-2 (SHA256: aa845861bbd4578700e10487d85b25ead8723ee98fbf143df7b7e0bf1cb3385d)",
        )
    }
  }

  @Test
  fun manifestRetained() {
    writeClass()
    projectScript.appendText(
      """
        $jarTask {
          manifest {
            attributes '$mainClassAttributeKey': 'my.Main'
            attributes '$TEST_ENTRY_ATTR_KEY': 'PASSED'
          }
        }
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    commonAssertions {
      assertThat(getValue(TEST_ENTRY_ATTR_KEY)).isEqualTo("PASSED")
      assertThat(getValue(mainClassAttributeKey)).isEqualTo("my.Main")
    }
  }

  @Test
  fun manifestTransformed() {
    writeClass()

    projectScript.appendText(MANIFEST_ATTRS)

    runWithSuccess(shadowJarPath)

    commonAssertions()
  }

  @Test // #427
  fun mergeLog4j2PluginCacheFiles() {
    val content = requireResourceAsPath(PLUGIN_CACHE_FILE).readText()
    val one = buildJarOne { insert(PLUGIN_CACHE_FILE, content) }
    val two = buildJarOne { insert(PLUGIN_CACHE_FILE, content) }
    projectScript.appendText(
      transform<Log4j2PluginsCacheFileTransformer>(
        dependenciesBlock = implementationFiles(one, two)
      )
    )

    runWithSuccess(shadowJarPath)

    val actualFileBytes = outputShadowedJar.use { jar ->
      jar.getStream(PLUGIN_CACHE_FILE).use { it.readAllBytes() }
    }
    assertThat(actualFileBytes.contentHashCode()).all {
      // Hash of the original plugin cache file.
      isNotEqualTo(-2114104185)
      isEqualTo(1911442937)
    }
  }

  @Test
  fun includeResource() {
    val foo = path("foo").apply { writeText("foo") }
    projectScript.appendText(
      @Suppress("DEPRECATION")
      transform<IncludeResourceTransformer>(
        transformerBlock =
          """
          resource = 'bar'
          file = file('${foo.invariantSeparatorsPathString}')
        """
            .trimIndent()
      )
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly("bar", *manifestEntries)
      getContent("bar").isEqualTo("foo")
    }
  }

  @Test
  fun excludeResource() {
    val one = buildJarOne {
      insert("foo", "bar")
      insert("bar", "foo")
    }
    projectScript.appendText(
      @Suppress("DEPRECATION")
      transform<DontIncludeResourceTransformer>(
        dependenciesBlock = implementationFiles(one),
        transformerBlock = "resource = 'foo'",
      )
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly("bar", *manifestEntries)
      getContent("bar").isEqualTo("foo")
    }
  }

  @Test
  fun preserveFirstFoundResource() {
    path("src/main/resources/foo/bar").writeText("bar1")
    path("src/main/resources/foo/baz").writeText("baz1")
    val one = buildJarOne {
      insert("foo/bar", "bar2")
      insert("foo/baz", "baz2")
    }
    val two = buildJarTwo {
      insert("foo/bar", "bar3")
      insert("foo/baz", "baz3")
    }
    projectScript.appendText(
      transform<PreserveFirstFoundResourceTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock = "resources = ['foo/bar']",
      )
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly("foo/", "foo/bar", "foo/baz", *manifestEntries)
      getContent("foo/bar").isEqualTo("bar1")
      getContent("foo/baz").isEqualTo("baz3")
    }
  }

  @Test
  fun useCustomTransformer() {
    projectScript.appendText(
      """
        dependencies {
          implementation 'my:a:1.0'
          implementation 'my:b:1.0'
        }
        $shadowJarTask {
          // Use Transformer.Companion (no-op) to mock a custom transformer here.
          transform(${ResourceTransformer.Companion::class.java.name})
        }
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll { containsOnly(*entriesInAB, *manifestEntries) }
  }

  @Test // #1626
  fun useApacheNoticeTransformerWithoutProjectName() {
    val noticeEntry = "META-INF/NOTICE"
    val one = buildJarOne {
      insert(
        noticeEntry,
        """
        Apache Commons DBCP
        Copyright 2001-2024 The Apache Software Foundation

        This product includes software developed at
        The Apache Software Foundation (https://www.apache.org/).
        """
          .trimIndent(),
      )
    }
    val two = buildJarTwo {
      insert(
        noticeEntry,
        """
        Apache Commons Pool
        Copyright 2001-2025 The Apache Software Foundation

        This product includes software developed at
        The Apache Software Foundation (https://www.apache.org/).
        """
          .trimIndent(),
      )
    }
    projectScript.appendText(
      transform<ApacheNoticeResourceTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock = "addHeader = false",
      )
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(noticeEntry, *manifestEntries)
      getContent(noticeEntry)
        .isEqualTo(
          """
          Apache Commons Pool
          Copyright 2001-2025 The Apache Software Foundation

          This product includes software developed at
          The Apache Software Foundation (https://www.apache.org/).

          Apache Commons DBCP
          Copyright 2001-2024 The Apache Software Foundation
          """
            .trimIndent()
        )
    }
  }

  @ParameterizedTest
  @MethodSource("transformerConfigProvider")
  fun otherTransformers(pair: Pair<String, KClass<*>>) {
    val (configuration, transformer) = pair
    projectScript.appendText(
      """
        dependencies {
          implementation 'my:a:1.0'
          implementation 'my:b:1.0'
        }
        $shadowJarTask {
          transform(${transformer.java.name}) $configuration
        }
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll { containsAtLeast(*entriesInAB) }
  }

  @Test
  fun configureComplexTransformerProperties() {
    val propertiesEntry = "META-INF/test.properties"
    val one = buildJarOne {
      insert(propertiesEntry, "foo=第一")
      insert("META-INF/LICENSE", "license one")
    }
    val two = buildJarTwo {
      insert(propertiesEntry, "foo=第二")
      insert("META-INF/LICENSE", "license two")
    }
    val artifactLicense = path("my-license").apply { writeText("artifact license text") }

    projectScript.appendText(
      """
        dependencies {
          ${implementationFiles(one, two)}
        }
        $shadowJarTask {
          transform(${PropertiesFileTransformer::class.java.name}) {
            mappings = [
              '$propertiesEntry': ['mergeStrategy': 'append', 'mergeSeparator': ';']
            ]
            charsetName = 'utf-8'
            keyTransformer = { key -> key.toUpperCase() }
          }
          transform(${MergeLicenseResourceTransformer::class.java.name}) {
            outputPath = 'MY_LICENSE'
            artifactLicense = file('${artifactLicense.invariantSeparatorsPathString}')
            firstSeparator = '####'
            separator = '----'
          }
        }
      """
        .trimIndent()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      getContent(propertiesEntry).contains("FOO=第一;第二")
      getContent("MY_LICENSE")
        .isEqualTo(
          """
          SPDX-License-Identifier: Apache-2.0
          artifact license text
          ####
          license one
          ----
          license two
          """
            .trimIndent()
        )
    }
  }

  private fun commonAssertions(
    mainAttributesBlock: JarAttribute.() -> Unit = {
      assertThat(getValue(TEST_ENTRY_ATTR_KEY)).isEqualTo("PASSED")
      assertThat(getValue(mainClassAttributeKey)).isEqualTo("my.Main")
      assertThat(getValue(NEW_ENTRY_ATTR_KEY)).isEqualTo("NEW")
    }
  ) {
    val mf = outputShadowedJar.use { it.manifest }
    assertThat(mf).isNotNull()
    mainAttributesBlock(mf.mainAttributes)
  }

  private companion object {
    const val NEW_ENTRY_ATTR_KEY = "New-Entry"
    const val TEST_ENTRY_ATTR_KEY = "Test-Entry"

    val MANIFEST_ATTRS =
      """
        $jarTask {
          manifest {
            attributes '$mainClassAttributeKey': 'my.Main'
            attributes '$TEST_ENTRY_ATTR_KEY': 'FAILED'
          }
        }
        $shadowJarTask {
          manifest {
            attributes '$NEW_ENTRY_ATTR_KEY': 'NEW'
            attributes '$TEST_ENTRY_ATTR_KEY': 'PASSED'
          }
        }
    """
        .trimIndent()

    @JvmStatic
    fun transformerConfigProvider() =
      listOf(
        "" to ApacheLicenseResourceTransformer::class,
        "" to ComponentsXmlResourceTransformer::class,
        "" to ManifestAppenderTransformer::class,
        "" to ManifestResourceTransformer::class,
      )
  }
}
