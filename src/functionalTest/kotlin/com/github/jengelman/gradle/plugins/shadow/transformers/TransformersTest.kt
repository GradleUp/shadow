package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.testkit.containsOnly
import com.github.jengelman.gradle.plugins.shadow.testkit.getContent
import com.github.jengelman.gradle.plugins.shadow.testkit.getStream
import com.github.jengelman.gradle.plugins.shadow.testkit.requireResourceAsPath
import java.util.jar.Attributes as JarAttribute
import kotlin.io.path.appendText
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor.PLUGIN_CACHE_FILE
import org.junit.jupiter.api.Test

class TransformersTest : BaseTransformerTest() {

  @Test
  fun manifestRetained() {
    writeClass()
    projectScript.appendText(
      """
        |$jarTask {
        |  manifest {
        |    attributes '$mainClassAttributeKey': 'my.Main'
        |    attributes '$TEST_ENTRY_ATTR_KEY': 'PASSED'
        |  }
        |}
      """
        .trimMargin()
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
        |dependencies {
        |  implementation 'my:a:1.0'
        |  implementation 'my:b:1.0'
        |}
        |$shadowJarTask {
        |  // Use Transformer.Companion (no-op) to mock a custom transformer here.
        |  transform(${ResourceTransformer.Companion::class.java.name})
        |}
      """
        .trimMargin()
    )

    runWithSuccess(shadowJarPath)

    assertThat(outputShadowedJar).useAll { containsOnly(*entriesInAB, *manifestEntries) }
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
        |$jarTask {
        |  manifest {
        |    attributes '$mainClassAttributeKey': 'my.Main'
        |    attributes '$TEST_ENTRY_ATTR_KEY': 'FAILED'
        |  }
        |}
        |$shadowJarTask {
        |  manifest {
        |    attributes '$NEW_ENTRY_ATTR_KEY': 'NEW'
        |    attributes '$TEST_ENTRY_ATTR_KEY': 'PASSED'
        |  }
        |}
    """
        .trimMargin()
  }
}
