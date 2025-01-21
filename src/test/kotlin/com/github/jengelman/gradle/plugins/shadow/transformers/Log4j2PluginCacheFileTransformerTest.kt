package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.fail
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.util.SimpleRelocator
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.util.Collections
import java.util.jar.JarInputStream
import org.apache.commons.io.IOUtils
import org.apache.logging.log4j.core.config.plugins.processor.PluginCache
import org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor.PLUGIN_CACHE_FILE
import org.apache.tools.zip.ZipOutputStream
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Log4j2PluginCacheFileTransformerTest {

  private var pluginUrl: URL? = null

  @BeforeAll
  fun setUp() {
    pluginUrl = Log4j2PluginCacheFileTransformerTest::class.java.getClassLoader()
      .getResource(PLUGIN_CACHE_FILE)
  }

  @AfterAll
  fun tearDown() {
    pluginUrl = null
  }

  @Test
  @Throws(Exception::class)
  fun test() {
    val transformer = Log4j2PluginsCacheFileTransformer()
    assertThat(transformer.hasTransformedResource()).isFalse()

    val expectedYoungestResourceTime = 1605922127000L // Sat Nov 21 2020 01:28:47
    javaClass.getClassLoader().getResourceAsStream(PLUGIN_CACHE_FILE)
      .use { log4jCacheFileInputStream ->
        transformer.transform(
          TransformerContext.builder()
            .path(PLUGIN_CACHE_FILE)
            .inputStream(requireNotNull(log4jCacheFileInputStream))
            .build(),
        )
      }
    assertThat(transformer.hasTransformedResource()).isTrue()

    javaClass.getClassLoader().getResourceAsStream(PLUGIN_CACHE_FILE)
      .use { log4jCacheFileInputStream ->
        transformer.transform(
          TransformerContext.builder()
            .path(PLUGIN_CACHE_FILE)
            .inputStream(requireNotNull(log4jCacheFileInputStream))
            .build(),
        )
      }
    assertThat(transformer.hasTransformedResource()).isTrue()

    assertTransformedCacheFile(transformer, expectedYoungestResourceTime, 1911442937)
  }

  @Throws(IOException::class)
  private fun assertTransformedCacheFile(
    transformer: Log4j2PluginsCacheFileTransformer,
    expectedTime: Long,
    expectedHash: Long,
  ) {
    val jarBuff = ByteArrayOutputStream()
    ZipOutputStream(jarBuff).use { out ->
      transformer.modifyOutputStream(requireNotNull(out), false)
    }
    JarInputStream(ByteArrayInputStream(jarBuff.toByteArray())).use { inputStream ->
      while (true) {
        val jarEntry = inputStream.nextJarEntry
        if (jarEntry == null) {
          fail("No expected resource in the output jar")
        } else if (jarEntry.getName() == PLUGIN_CACHE_FILE) {
          assertThat(expectedTime).isEqualTo(jarEntry.getTime())
          assertThat(expectedHash).isEqualTo(IOUtils.toByteArray(inputStream).contentHashCode().toLong())
          break
        }
      }
    }
  }

  @Test
  @Throws(IOException::class)
  fun testRelocation() {
    // test with matching relocator
    testRelocation("org.apache.logging", "new.location.org.apache.logging", "new.location.org.apache.logging")

    // test without matching relocator
    testRelocation("com.apache.logging", "new.location.com.apache.logging", "org.apache.logging")
  }

  @Throws(IOException::class)
  private fun testRelocation(src: String?, pattern: String?, target: String) {
    val transformer = Log4j2PluginsCacheFileTransformer()
    val log4jRelocator: Relocator = SimpleRelocator(src, pattern)
    val aggregator = PluginCache()
    aggregator.loadCacheFiles(Collections.enumeration<URL?>(mutableListOf<URL?>(pluginUrl)))

    transformer.relocatePlugin(listOf(log4jRelocator), aggregator.allCategories)

    for (pluginEntryMap in aggregator.allCategories.values) {
      for (entry in pluginEntryMap.values) {
        assertThat(entry.className.startsWith(target)).isTrue()
      }
    }
  }
}
