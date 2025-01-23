package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.fail
import com.github.jengelman.gradle.plugins.shadow.internal.requireResourceAsStream
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.util.SimpleRelocator
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.net.URL
import java.util.Collections
import java.util.jar.JarInputStream
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
  fun shouldTransform() {
    transformer.transform(context(SimpleRelocator()))
    assertThat(transformer.hasTransformedResource()).isTrue()
  }

  @Test
  fun shouldTransformForSingleFile() {
    transformer.transform(context())
    assertThat(transformer.hasTransformedResource()).isTrue()
  }

  @Test
  fun relocateClassesInsideDatFile() {
    val relocator = SimpleRelocator("org.apache.logging", "new.location.org.apache.logging")
    transformer.transform(context(relocator))
    assertThat(transformer.hasTransformedResource()).isTrue()

    // Write out to a fake jar file
    val testableZipFile = File.createTempFile("testable-zip-file-", ".jar")
    ZipOutputStream(testableZipFile.outputStream().buffered()).use { zipOutputStream ->
      transformer.modifyOutputStream(zipOutputStream, true)
    }

    // Pull the data back out and make sure it was transformed
    val cache = PluginCache()
    val url = URI("jar:" + testableZipFile.toURI().toURL() + "!/" + PLUGIN_CACHE_FILE).toURL()
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
          @Suppress("Since15")
          assertThat(1911442937L).isEqualTo(inputStream.readAllBytes().contentHashCode().toLong())
          break
        }
      }
    }
  }

  @ParameterizedTest
  @MethodSource("relocationParameters")
  fun relocations(pattern: String, shadedPattern: String, target: String) {
    val log4jRelocator = SimpleRelocator(pattern, shadedPattern)
    val aggregator = PluginCache().apply {
      loadCacheFiles(Collections.enumeration<URL>(listOf(pluginCacheUrl)))
    }
    // Init stats to avoid NPE.
    transformer.transform(context())
    transformer.relocatePlugin(listOf(log4jRelocator), aggregator.allCategories)

    for (pluginEntryMap in aggregator.allCategories.values) {
      for (entry in pluginEntryMap.values) {
        assertThat(entry.className.startsWith(target)).isTrue()
      }
    }
  }

  private fun context(vararg relocator: SimpleRelocator): TransformerContext {
    return TransformerContext(PLUGIN_CACHE_FILE, requireResourceAsStream(PLUGIN_CACHE_FILE), relocator.toSet())
  }

  private companion object {
    val pluginCacheUrl: URL = requireNotNull(this::class.java.classLoader.getResource(PLUGIN_CACHE_FILE))

    @JvmStatic
    fun relocationParameters() = listOf(
      // test with matching relocator
      Arguments.of("org.apache.logging", "new.location.org.apache.logging", "new.location.org.apache.logging"),
      // test without matching relocator
      Arguments.of("com.apache.logging", "new.location.com.apache.logging", "org.apache.logging"),
    )
  }
}
