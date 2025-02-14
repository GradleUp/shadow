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
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
import com.github.jengelman.gradle.plugins.shadow.util.getStream
import java.util.jar.Attributes
import kotlin.io.path.appendText
import kotlin.io.path.writeText
import kotlin.reflect.KClass
import org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor.PLUGIN_CACHE_FILE
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class TransformersTest : BaseTransformerTest() {
  @Test
  fun manifestRetained() {
    writeMainClass()
    projectScriptPath.appendText(
      """
        jar {
          manifest {
            attributes '$mainClassAttributeKey': 'shadow.Main'
            attributes '$TEST_ENTRY_ATTR_KEY': 'PASSED'
          }
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    commonAssertions {
      assertThat(getValue(TEST_ENTRY_ATTR_KEY)).isEqualTo("PASSED")
      assertThat(getValue(mainClassAttributeKey)).isEqualTo("shadow.Main")
    }
  }

  @Test
  fun manifestTransformed() {
    writeMainClass()

    projectScriptPath.appendText(MANIFEST_ATTRS)

    run(shadowJarTask)

    commonAssertions()
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/82",
  )
  @Test
  fun shadowManifestLeaksToJarManifest() {
    writeMainClass()
    projectScriptPath.appendText(MANIFEST_ATTRS)

    run("jar", shadowJarTask)

    commonAssertions()

    val mf = jarPath("build/libs/shadow-1.0.jar").use { it.manifest }
    assertThat(mf).isNotNull()
    assertThat(mf.mainAttributes.getValue(TEST_ENTRY_ATTR_KEY)).isEqualTo("FAILED")
    assertThat(mf.mainAttributes.getValue(mainClassAttributeKey)).isEqualTo("shadow.Main")
    assertThat(mf.mainAttributes.getValue(NEW_ENTRY_ATTR_KEY)).isNull()
  }

  @Issue(
    "https://github.com/GradleUp/shadow/issues/427",
  )
  @Test
  fun canMergeLog4j2PluginCacheFiles() {
    val content = requireResourceAsText(PLUGIN_CACHE_FILE)
    val one = buildJarOne {
      insert(PLUGIN_CACHE_FILE, content)
    }
    val two = buildJarOne {
      insert(PLUGIN_CACHE_FILE, content)
    }
    projectScriptPath.appendText(
      transform<Log4j2PluginsCacheFileTransformer>(
        dependenciesBlock = implementationFiles(one, two),
      ),
    )

    run(shadowJarTask)

    val actualFileBytes = outputShadowJar.use { jar ->
      jar.getStream(PLUGIN_CACHE_FILE).use { it.readAllBytes() }
    }
    assertThat(actualFileBytes.contentHashCode()).all {
      // Hash of the original plugin cache file.
      isNotEqualTo(-2114104185)
      isEqualTo(1911442937)
    }
  }

  @Test
  fun canUseCustomTransformer() {
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'shadow:a:1.0'
          implementation 'shadow:b:1.0'
        }
        $shadowJar {
          // Use NoOpTransformer to mock a custom transformer here.
          transform(${NoOpTransformer::class.java.name}.INSTANCE)
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsEntries(*entriesInAB)
    }
  }

  @ParameterizedTest
  @MethodSource("transformerConfigProvider")
  fun otherTransformers(pair: Pair<String, KClass<*>>) {
    val (configuration, transformer) = pair
    if (configuration.contains("test/some.file")) {
      path("test/some.file").writeText("some content")
    }
    projectScriptPath.appendText(
      """
        dependencies {
          implementation 'shadow:a:1.0'
          implementation 'shadow:b:1.0'
        }
        $shadowJar {
          transform(${transformer.java.name}) $configuration
        }
      """.trimIndent(),
    )

    run(shadowJarTask)

    assertThat(outputShadowJar).useAll {
      containsEntries(*entriesInAB)
    }
  }

  private fun commonAssertions(
    mainAttributesBlock: Attributes.() -> Unit = {
      assertThat(getValue(TEST_ENTRY_ATTR_KEY)).isEqualTo("PASSED")
      assertThat(getValue(mainClassAttributeKey)).isEqualTo("shadow.Main")
      assertThat(getValue(NEW_ENTRY_ATTR_KEY)).isEqualTo("NEW")
    },
  ) {
    val mf = outputShadowJar.use { it.manifest }
    assertThat(mf).isNotNull()
    mainAttributesBlock(mf.mainAttributes)
  }

  private companion object {
    const val NEW_ENTRY_ATTR_KEY = "New-Entry"
    const val TEST_ENTRY_ATTR_KEY = "Test-Entry"

    val MANIFEST_ATTRS = """
        jar {
          manifest {
            attributes '$mainClassAttributeKey': 'shadow.Main'
            attributes '$TEST_ENTRY_ATTR_KEY': 'FAILED'
          }
        }
        $shadowJar {
          manifest {
            attributes '$NEW_ENTRY_ATTR_KEY': 'NEW'
            attributes '$TEST_ENTRY_ATTR_KEY': 'PASSED'
          }
        }
    """.trimIndent()

    @JvmStatic
    fun transformerConfigProvider() = listOf(
      "" to ApacheLicenseResourceTransformer::class,
      "" to ApacheNoticeResourceTransformer::class,
      "" to ComponentsXmlResourceTransformer::class,
      "" to DontIncludeResourceTransformer::class,
      "{ resource.set(\"test.file\"); file.fileValue(file(\"test/some.file\")) }" to IncludeResourceTransformer::class,
      "" to ManifestAppenderTransformer::class,
      "" to ManifestResourceTransformer::class,
      "{ keyTransformer = { it.toLowerCase() } }" to PropertiesFileTransformer::class,
    )
  }
}
