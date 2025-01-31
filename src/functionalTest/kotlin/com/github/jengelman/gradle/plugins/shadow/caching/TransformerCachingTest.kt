package com.github.jengelman.gradle.plugins.shadow.caching

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.github.jengelman.gradle.plugins.shadow.transformers.ApacheLicenseResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ApacheNoticeResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ComponentsXmlResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.DontIncludeResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.IncludeResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ManifestAppenderTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ManifestResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.XmlAppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.util.containsEntries
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
  @BeforeEach
  override fun setup() {
    super.setup()
    writeMainClass()
  }

  @Test
  fun shadowJarIsCachedCorrectlyWhenUsingServiceFileTransformer() {
    val assertions = {
      assertThat(outputShadowJar).useAll {
        containsEntries("shadow/Main.class")
      }
    }

    assertExecutionSuccess()
    assertions()

    projectScriptPath.appendText(
      transform<ServiceFileTransformer>(
        transformerBlock = """
          path = 'META-INF/foo'
        """.trimIndent(),
      ),
    )
    assertExecutionSuccess()
    assertions()

    assertExecutionsFromCacheAndUpToDate()
    assertions()

    val replaced = projectScriptPath.readText().replace("META-INF/foo", "META-INF/bar")
    projectScriptPath.writeText(replaced)

    assertExecutionSuccess()
    assertions()

    assertExecutionsFromCacheAndUpToDate()
    assertions()
  }

  @Test
  fun shadowJarIsCachedCorrectlyWhenUsingAppendingTransformer() {
    path("src/main/resources/foo/bar.properties").writeText("foo=bar")
    val assertions = { name: String ->
      assertThat(outputShadowJar).useAll {
        containsEntries("shadow/Main.class", "foo/$name.properties")
        getContent("foo/$name.properties").isEqualTo("foo=$name")
      }
    }

    assertExecutionSuccess()
    assertions("bar")

    projectScriptPath.appendText(
      transform<AppendingTransformer>(
        transformerBlock = """
          resource = 'foo/bar.properties'
        """.trimIndent(),
      ),
    )
    assertExecutionSuccess()
    assertions("bar")

    assertExecutionsFromCacheAndUpToDate()
    assertions("bar")

    path("src/main/resources/foo/bar.properties").deleteExisting()
    path("src/main/resources/foo/baz.properties").writeText("foo=baz")
    val replaced = projectScriptPath.readText().replace("foo/bar.properties", "foo/baz.properties")
    projectScriptPath.writeText(replaced)

    assertExecutionSuccess()
    assertions("baz")

    assertExecutionsFromCacheAndUpToDate()
    assertions("baz")
  }

  @Test
  fun shadowJarIsCachedCorrectlyWhenUsingXmlAppendingTransformer() {
    path("src/main/resources/foo/bar.xml").writeText("<foo>bar</foo>")
    val assertions = { name: String ->
      assertThat(outputShadowJar).useAll {
        containsEntries("shadow/Main.class", "foo/$name.xml")
        getContent("foo/$name.xml").contains("<foo>$name</foo>")
      }
    }

    assertExecutionSuccess()
    assertions("bar")

    projectScriptPath.appendText(
      transform<XmlAppendingTransformer>(
        transformerBlock = """
          resource = 'foo/bar.xml'
        """.trimIndent(),
      ),
    )
    assertExecutionSuccess()
    assertions("bar")

    assertExecutionsFromCacheAndUpToDate()
    assertions("bar")

    path("src/main/resources/foo/bar.xml").deleteExisting()
    path("src/main/resources/foo/baz.xml").writeText("<foo>baz</foo>")
    val replaced = projectScriptPath.readText().replace("foo/bar.xml", "foo/baz.xml")
    projectScriptPath.writeText(replaced)

    assertExecutionSuccess()
    assertions("baz")

    assertExecutionsFromCacheAndUpToDate()
    assertions("baz")
  }

  @ParameterizedTest
  @MethodSource("transformerConfigProvider")
  fun shadowJarIsCachedCorrectlyWhenUsingOtherTransformers(pair: Pair<String, KClass<*>>) {
    val (configuration, transformer) = pair
    if (configuration.contains("test/some.file")) {
      path("test/some.file").writeText("some content")
    }
    val assertions = {
      assertThat(outputShadowJar).useAll {
        containsEntries("shadow/Main.class")
      }
    }

    assertExecutionSuccess()
    assertions()

    projectScriptPath.appendText(
      """
        $shadowJar {
          transform(${transformer.java.name}) $configuration
        }
      """.trimIndent(),
    )
    assertExecutionSuccess()
    assertions()

    assertExecutionsFromCacheAndUpToDate()
    assertions()
  }

  private companion object {
    @JvmStatic
    fun transformerConfigProvider() = listOf(
      "" to ApacheLicenseResourceTransformer::class,
      "" to ApacheNoticeResourceTransformer::class,
      "" to ComponentsXmlResourceTransformer::class,
      "" to DontIncludeResourceTransformer::class,
      "" to GroovyExtensionModuleTransformer::class,
      "{ resource.set(\"test.file\"); file.fileValue(file(\"test/some.file\")) }" to IncludeResourceTransformer::class,
      "" to Log4j2PluginsCacheFileTransformer::class,
      "" to ManifestAppenderTransformer::class,
      "" to ManifestResourceTransformer::class,
      "{ keyTransformer = { it.toLowerCase() } }" to PropertiesFileTransformer::class,
    )
  }
}
