package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.testkit.containsAtLeast
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.testkit.getStream
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsPath
import java.util.jar.Attributes as JarAttribute
import kotlin.io.path.appendText
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.reflect.KClass
import org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor.PLUGIN_CACHE_FILE
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class TransformersTest : BaseTransformerTest() {

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
        "{ exclude('not-found') }" to DeduplicatingResourceTransformer::class,
        "" to GroovyExtensionModuleTransformer::class,
        "" to Log4j2PluginsCacheFileTransformer::class,
        "" to ManifestAppenderTransformer::class,
        "" to ManifestResourceTransformer::class,
        "{ include('not-found') }" to PreserveFirstFoundResourceTransformer::class,
        "{ resource = 'not-found' }" to XmlAppendingTransformer::class,
      )
  }
}
