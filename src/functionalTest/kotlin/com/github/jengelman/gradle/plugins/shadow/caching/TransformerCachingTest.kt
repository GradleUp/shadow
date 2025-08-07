package com.github.jengelman.gradle.plugins.shadow.caching

import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.transformers.ApacheLicenseResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ApacheNoticeResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ComponentsXmlResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ManifestAppenderTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ManifestResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.XmlAppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.util.containsAtLeast
import com.github.jengelman.gradle.plugins.shadow.util.containsOnly
import com.github.jengelman.gradle.plugins.shadow.util.getContent
import kotlin.io.path.appendText
import kotlin.io.path.deleteExisting
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.reflect.KClass
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class TransformerCachingTest : BaseCachingTest() {
  private lateinit var mainClass: String

  @BeforeEach
  override fun setup() {
    super.setup()
    mainClass = writeClass()
  }

  @Test
  fun serviceFileTransformerPropsChanged() {
    val assertions = {
      assertCompositeExecutions {
        containsOnly(
          "my/",
          mainClass,
          *manifestEntries,
        )
      }
    }

    assertions()

    projectScriptPath.appendText(
      transform<ServiceFileTransformer>(
        transformerBlock = """
          path = 'META-INF/foo'
        """.trimIndent(),
      ),
    )

    assertions()

    val replaced = projectScriptPath.readText().replace("META-INF/foo", "META-INF/bar")
    projectScriptPath.writeText(replaced)

    assertions()
  }

  @Test
  fun appendingTransformerPropsChanged() {
    path("src/main/resources/foo/bar.properties").writeText("foo=bar")
    val assertions = { name: String ->
      assertCompositeExecutions {
        containsOnly(
          "my/",
          "foo/",
          "foo/$name.properties",
          mainClass,
          *manifestEntries,
        )
        getContent("foo/$name.properties").isEqualTo("foo=$name")
      }
    }

    assertions("bar")

    projectScriptPath.appendText(
      transform<AppendingTransformer>(
        transformerBlock = """
          resource = 'foo/bar.properties'
        """.trimIndent(),
      ),
    )

    assertions("bar")

    path("src/main/resources/foo/bar.properties").deleteExisting()
    path("src/main/resources/foo/baz.properties").writeText("foo=baz")
    val replaced = projectScriptPath.readText().replace("foo/bar.properties", "foo/baz.properties")
    projectScriptPath.writeText(replaced)

    assertions("baz")
  }

  @Test
  fun xmlAppendingTransformerPropsChanged() {
    path("src/main/resources/foo/bar.xml").writeText("<foo>bar</foo>")
    val assertions = { name: String ->
      assertCompositeExecutions {
        containsOnly(
          "my/",
          "foo/",
          "foo/$name.xml",
          mainClass,
          *manifestEntries,
        )
        getContent("foo/$name.xml").contains("<foo>$name</foo>")
      }
    }

    assertions("bar")

    projectScriptPath.appendText(
      transform<XmlAppendingTransformer>(
        transformerBlock = """
          resource = 'foo/bar.xml'
        """.trimIndent(),
      ),
    )

    assertions("bar")

    path("src/main/resources/foo/bar.xml").deleteExisting()
    path("src/main/resources/foo/baz.xml").writeText("<foo>baz</foo>")
    val replaced = projectScriptPath.readText().replace("foo/bar.xml", "foo/baz.xml")
    projectScriptPath.writeText(replaced)

    assertions("baz")
  }

  @Test
  fun disableCacheIfAnyTransformerIsNotCacheable() {
    projectScriptPath.appendText(
      """
        $shadowJar {
          mergeServiceFiles()
        }
      """.trimIndent() + lineSeparator,
    )

    assertCompositeExecutions()

    projectScriptPath.appendText(
      """
        $shadowJar {
          mergeGroovyExtensionModules()
        }
      """.trimIndent() + lineSeparator,
    )

    assertCompositeExecutions()

    projectScriptPath.appendText(
      """
        $shadowJar {
          // Use Transformer.Companion (no-op) to mock a custom transformer here, it's not cacheable.
          transform(${ResourceTransformer.Companion::class.java.name})
        }
      """.trimIndent(),
    )

    assertExecutionSuccess()
    cleanOutputs()
    // The shadowJar task should be executed again as the cache is disabled.
    assertExecutionSuccess()
  }

  @ParameterizedTest
  @MethodSource("transformerConfigProvider")
  fun otherTransformers(pair: Pair<String, KClass<*>>) {
    val (configuration, transformer) = pair
    val assertions = {
      assertCompositeExecutions {
        containsAtLeast(mainClass)
      }
    }

    assertions()

    projectScriptPath.appendText(
      """
        $shadowJar {
          transform(${transformer.java.name}) $configuration
        }
      """.trimIndent(),
    )

    assertions()
  }

  private companion object {
    @JvmStatic
    fun transformerConfigProvider() = listOf(
      "" to ApacheLicenseResourceTransformer::class,
      "" to ApacheNoticeResourceTransformer::class,
      "" to ComponentsXmlResourceTransformer::class,
      "" to GroovyExtensionModuleTransformer::class,
      "" to Log4j2PluginsCacheFileTransformer::class,
      "" to ManifestAppenderTransformer::class,
      "" to ManifestResourceTransformer::class,
      "{ keyTransformer = { it.toLowerCase() } }" to PropertiesFileTransformer::class,
    )
  }
}
