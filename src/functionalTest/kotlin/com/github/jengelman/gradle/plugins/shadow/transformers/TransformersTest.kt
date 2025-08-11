package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.github.jengelman.gradle.plugins.shadow.internal.mainClassAttributeKey
import com.github.jengelman.gradle.plugins.shadow.internal.requireResourceAsText
import com.github.jengelman.gradle.plugins.shadow.util.Issue
import com.github.jengelman.gradle.plugins.shadow.util.containsAtLeast
import com.github.jengelman.gradle.plugins.shadow.util.containsOnly
import com.github.jengelman.gradle.plugins.shadow.util.getContent
import com.github.jengelman.gradle.plugins.shadow.util.getStream
import java.util.jar.Attributes as JarAttribute
import kotlin.io.path.appendText
import kotlin.io.path.invariantSeparatorsPathString
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
      """.trimIndent(),
    )

    run(shadowJarPath)

    commonAssertions {
      assertThat(getValue(TEST_ENTRY_ATTR_KEY)).isEqualTo("PASSED")
      assertThat(getValue(mainClassAttributeKey)).isEqualTo("my.Main")
    }
  }

  @Test
  fun manifestTransformed() {
    writeClass()

    projectScript.appendText(MANIFEST_ATTRS)

    run(shadowJarPath)

    commonAssertions()
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/82",
  )
  @Test
  fun shadowManifestLeaksToJarManifest() {
    writeClass()
    projectScript.appendText(MANIFEST_ATTRS)

    run("jar", shadowJarPath)

    commonAssertions()

    val mf = jarPath("build/libs/my-1.0.jar").use { it.manifest }
    assertThat(mf).isNotNull()
    assertThat(mf.mainAttributes.getValue(TEST_ENTRY_ATTR_KEY)).isEqualTo("FAILED")
    assertThat(mf.mainAttributes.getValue(mainClassAttributeKey)).isEqualTo("my.Main")
    assertThat(mf.mainAttributes.getValue(NEW_ENTRY_ATTR_KEY)).isNull()
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/427",
  )
  @Test
  fun mergeLog4j2PluginCacheFiles() {
    val content = requireResourceAsText(PLUGIN_CACHE_FILE)
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

    run(shadowJarPath)

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

    run(shadowJarPath)

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

    run(shadowJarPath)

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

    run(shadowJarPath)

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

    run(shadowJarPath)

    assertThat(outputShadowedJar).useAll {
      containsOnly(
        *entriesInAB,
        *manifestEntries,
      )
    }
  }

  @Test
  fun apacheNoticeTransformerWithRealDependencies() {
    projectScript.appendText(
      """
        dependencies {
          implementation 'org.apache.commons:commons-dbcp2:2.13.0'
          implementation 'org.apache.commons:commons-pool2:2.12.0'
        }
        $shadowJarTask {
          transform(${ApacheNoticeResourceTransformer::class.java.name}) {
            addHeader = false
          }
        }
      """.trimIndent(),
    )

    run(shadowJarPath) // This will fail
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

    run(shadowJarPath)

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
}
