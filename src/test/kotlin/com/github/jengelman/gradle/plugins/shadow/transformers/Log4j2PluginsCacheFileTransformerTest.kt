package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import assertk.assertions.startsWith
import assertk.fail
import com.github.jengelman.gradle.plugins.shadow.internal.requireResourceAsStream
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.util.zipOutputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URL
import java.util.Collections
import java.util.jar.JarInputStream
import kotlin.io.path.createTempFile
import kotlin.io.path.outputStream
import org.apache.logging.log4j.core.config.plugins.processor.PluginCache
import org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor.PLUGIN_CACHE_FILE
import org.apache.tools.zip.ZipOutputStream
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * Modified from [org.apache.logging.log4j.maven.plugins.shade.transformer.Log4j2PluginCacheFileTransformerTest.java](https://github.com/apache/logging-log4j-transform/blob/main/log4j-transform-maven-shade-plugin-extensions/src/test/java/org/apache/logging/log4j/maven/plugins/shade/transformer/Log4j2PluginCacheFileTransformerTest.java).
 */
class Log4j2PluginsCacheFileTransformerTest : BaseTransformerTest<Log4j2PluginsCacheFileTransformer>() {
  @Test
  fun canTransformResource() {
    assertThat(transformer.canTransformResource("")).isFalse()
    assertThat(transformer.canTransformResource(".")).isFalse()
    assertThat(transformer.canTransformResource("tmp.dat")).isFalse()
    assertThat(transformer.canTransformResource("$PLUGIN_CACHE_FILE.tmp")).isFalse()
    assertThat(transformer.canTransformResource("tmp/$PLUGIN_CACHE_FILE")).isFalse()
    assertThat(transformer.canTransformResource(PLUGIN_CACHE_FILE)).isTrue()
  }

  @Test
  fun relocateClassesInsideDatFile() {
    val relocator = SimpleRelocator("org.apache.logging", "new.location.org.apache.logging")
    transformer.transform(context(relocator))
    assertThat(transformer.hasTransformedResource()).isTrue()

    val tempJar = createTempFile("testable-zip-file-", ".jar")
    tempJar.outputStream().zipOutputStream().use { zos ->
      transformer.modifyOutputStream(zos, true)
    }

    // Pull the data back out and make sure it was transformed
    val cache = PluginCache()
    val url = URI("jar:" + tempJar.toUri().toURL() + "!/" + PLUGIN_CACHE_FILE).toURL()
    val resources = Collections.enumeration(listOf(url))
    cache.loadCacheFiles(resources)

    assertThat(cache.getCategory("lookup")["date"]?.className)
      .isEqualTo("new.location.org.apache.logging.log4j.core.lookup.DateLookup")
  }

  @Test
  fun transformAndModifyOutputStream() {
    assertThat(transformer.hasTransformedResource()).isFalse()

    transformer.transform(context())
    assertThat(transformer.hasTransformedResource()).isTrue()
    transformer.transform(context())
    assertThat(transformer.hasTransformedResource()).isTrue()

    val jarBuff = ByteArrayOutputStream()
    ZipOutputStream(jarBuff).use {
      transformer.modifyOutputStream(it, false)
    }
    JarInputStream(jarBuff.toByteArray().inputStream()).use { inputStream ->
      while (true) {
        val jarEntry = inputStream.nextJarEntry
        if (jarEntry == null) {
          fail("No expected resource in the output jar.")
        } else if (jarEntry.name == PLUGIN_CACHE_FILE) {
          assertThat(inputStream.readAllBytes().contentHashCode()).all {
            // Hash of the original plugin cache file.
            isNotEqualTo(-2114104185)
            isEqualTo(1911442937)
          }
          break
        }
      }
    }
  }

  @ParameterizedTest
  @MethodSource("relocationProvider")
  fun relocations(pattern: String, shadedPattern: String, expected: String) {
    val aggregator = PluginCache().apply {
      val resources = Collections.enumeration(listOf(pluginCacheUrl))
      loadCacheFiles(resources)
    }
    transformer.transform(context(SimpleRelocator(pattern, shadedPattern)))
    transformer.relocatePlugins(aggregator)

    for (pluginEntryMap in aggregator.allCategories.values) {
      for (entry in pluginEntryMap.values) {
        assertThat(entry.className).startsWith(expected)
      }
    }
  }

  private companion object {
    val pluginCacheUrl: URL = requireNotNull(this::class.java.classLoader.getResource(PLUGIN_CACHE_FILE))

    fun context(vararg relocators: Relocator): TransformerContext {
      return TransformerContext(PLUGIN_CACHE_FILE, requireResourceAsStream(PLUGIN_CACHE_FILE), relocators.toSet())
    }

    @JvmStatic
    fun relocationProvider() = listOf(
      // test with matching relocator
      Arguments.of("org.apache.logging", "new.location.org.apache.logging", "new.location.org.apache.logging"),
      // test without matching relocator
      Arguments.of("com.apache.logging", "new.location.com.apache.logging", "org.apache.logging"),
    )
  }
}
