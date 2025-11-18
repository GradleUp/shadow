package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isSameInstanceAs
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.testkit.containsAtLeast
import com.github.jengelman.gradle.plugins.shadow.testkit.containsExactlyInAnyOrder
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.testkit.getContents
import com.github.jengelman.gradle.plugins.shadow.testkit.getStream
import com.github.jengelman.gradle.plugins.shadow.testkit.invariantEolString
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsPath
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import java.util.jar.Attributes as JarAttribute
import kotlin.booleanArrayOf
import kotlin.io.path.appendText
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.reflect.KClass
import org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor.PLUGIN_CACHE_FILE
import org.gradle.testkit.runner.TaskOutcome
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
        transformerBlock = """
          exclude("multiple-contents")
          ${if (excludeAll) "exclude(\"differing-content-2\")" else ""}
        """.trimIndent(),
      ),
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
        getContents("multiple-contents").containsExactlyInAnyOrder("content", "content-is-different")
        getContent("single-source").isEqualTo("content")
        getContent("same-content-twice").isEqualTo("content")
        getContents("differing-content-2").containsExactlyInAnyOrder("content", "content-is-different")
      }
    } else {
      val buildResult = runWithFailure(shadowJarPath)
      assertThat(buildResult.task(":shadowJar")!!.outcome).isSameInstanceAs(TaskOutcome.FAILED)
      assertThat(buildResult.output).contains(
        // Keep this list approach for Unix/Windows test compatibility.
        "Execution failed for task ':shadowJar'.",
        "> Found 1 path duplicate(s) with different content in the shadow JAR:",
        "    * differing-content-2",
        "differing-content-2 (Hash: -1337566116240053116)",
        "differing-content-2 (Hash: -6159701213549668473)",
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
      """.trimIndent(),
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

  @Issue(
    "https://github.com/GradleUp/shadow/issues/427",
  )
  @Test
  fun mergeLog4j2PluginCacheFiles() {
    val content = requireResourceAsPath(PLUGIN_CACHE_FILE).readText()
    val one = buildJarOne {
      insert(PLUGIN_CACHE_FILE, content)
    }
    val two = buildJarOne {
      insert(PLUGIN_CACHE_FILE, content)
    }
    projectScript.appendText(
      transform<Log4j2PluginsCacheFileTransformer>(
        dependenciesBlock = implementationFiles(one, two),
      ),
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
      transform<IncludeResourceTransformer>(
        transformerBlock = """
          resource = 'bar'
          file = file('${foo.invariantSeparatorsPathString}')
        """.trimIndent(),
      ),
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "bar",
        *manifestEntries,
      )
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
      transform<DontIncludeResourceTransformer>(
        dependenciesBlock = implementationFiles(one),
        transformerBlock = "resource = 'foo'",
      ),
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "bar",
        *manifestEntries,
      )
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
      ),
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "foo/",
        "foo/bar",
        "foo/baz",
        *manifestEntries,
      )
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
      """.trimIndent(),
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        *entriesInAB,
        *manifestEntries,
      )
    }
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/1626",
  )
  @Test
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
        """.trimIndent(),
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
        """.trimIndent(),
      )
    }
    projectScript.appendText(
      transform<ApacheNoticeResourceTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock = "addHeader = false",
      ),
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        noticeEntry,
        *manifestEntries,
      )
      getContent(noticeEntry).isEqualTo(
        """
        Apache Commons Pool
        Copyright 2001-2025 The Apache Software Foundation

        This product includes software developed at
        The Apache Software Foundation (https://www.apache.org/).

        Apache Commons DBCP
        Copyright 2001-2024 The Apache Software Foundation
        """.trimIndent(),
      )
    }
  }

  @Test
  fun overrideOutputPathOfNoticeFile() {
    val noticeEntry = "META-INF/NOTICE"
    val customNoticeEntry = "META-INF/CUSTOM_NOTICE"
    val one = buildJarOne {
      insert(noticeEntry, "Notice from A")
    }
    val two = buildJarTwo {
      insert(noticeEntry, "Notice from B")
    }
    projectScript.appendText(
      transform<ApacheNoticeResourceTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock = "addHeader = false; outputPath = '$customNoticeEntry'",
      ),
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        customNoticeEntry,
        *manifestEntries,
      )
      getContent(customNoticeEntry).isEqualTo(
        """
          Copyright 2006-2025 The Apache Software Foundation

          This product includes software developed at
          The Apache Software Foundation (https://www.apache.org/).

          Notice from A

          Notice from B
        """.trimIndent(),
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
      """.trimIndent(),
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsAtLeast(*entriesInAB)
    }
  }

  private fun commonAssertions(
    mainAttributesBlock: JarAttribute.() -> Unit = {
      assertThat(getValue(TEST_ENTRY_ATTR_KEY)).isEqualTo("PASSED")
      assertThat(getValue(mainClassAttributeKey)).isEqualTo("my.Main")
      assertThat(getValue(NEW_ENTRY_ATTR_KEY)).isEqualTo("NEW")
    },
  ) {
    val mf = outputShadowedJar.use { it.manifest }
    assertThat(mf).isNotNull()
    mainAttributesBlock(mf.mainAttributes)
  }

  private companion object {
    const val NEW_ENTRY_ATTR_KEY = "New-Entry"
    const val TEST_ENTRY_ATTR_KEY = "Test-Entry"

    val MANIFEST_ATTRS = """
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
    """.trimIndent()

    @JvmStatic
    fun transformerConfigProvider() = listOf(
      "" to ApacheLicenseResourceTransformer::class,
      "" to ComponentsXmlResourceTransformer::class,
      "" to ManifestAppenderTransformer::class,
      "" to ManifestResourceTransformer::class,
    )
  }

  @Test
  fun mergeLicenseResourceTransformer() {
    val one = buildJarOne {
      insert("META-INF/LICENSE", "license one")
    }
    val two = buildJarTwo {
      insert("META-INF/LICENSE", "license two")
    }
    val artifactLicense = path("my-license")
    artifactLicense.writeText("artifact license text")

    projectScript.appendText(
      transform<MergeLicenseResourceTransformer>(
        dependenciesBlock = implementationFiles(one, two),
        transformerBlock = """
          outputPath = 'MY_LICENSE'
          artifactLicense = file('${artifactLicense.invariantSeparatorsPathString}')
          firstSeparator = '####'
          separator = '----'
        """.trimIndent(),
      ),
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        "MY_LICENSE",
        "META-INF/",
        "META-INF/MANIFEST.MF",
      )
      getContent("MY_LICENSE").transform { it.invariantEolString }.isEqualTo(
        """
          SPDX-License-Identifier: Apache-2.0
          artifact license text
          ####
          license one
          ----
          license two
        """.trimIndent(),
      )
    }
  }
}
