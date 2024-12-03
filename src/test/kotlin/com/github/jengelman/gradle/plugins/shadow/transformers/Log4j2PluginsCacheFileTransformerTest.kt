package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URL
import java.util.Collections
import org.apache.logging.log4j.core.config.plugins.processor.PluginCache
import org.apache.tools.zip.ZipOutputStream
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class Log4j2PluginsCacheFileTransformerTest : TransformerTestSupport<Log4j2PluginsCacheFileTransformer>() {

  @BeforeEach
  fun setup() {
    transformer = Log4j2PluginsCacheFileTransformer()
  }

  @Test
  fun `should transform for a single file`() {
    transformer.transform(TransformerContext(PLUGIN_CACHE_FILE, getResourceStream()))
    assertThat(transformer.hasTransformedResource()).isTrue()
  }

  @Test
  fun `should transform`() {
    val relocators = listOf(SimpleRelocator())
    transformer.transform(TransformerContext(PLUGIN_CACHE_FILE, getResourceStream(), relocators))
    assertThat(transformer.hasTransformedResource()).isTrue()
  }

  @Test
  fun `relocate classes inside DAT file`() {
    val pattern = "org.apache.logging"
    val destination = "new.location.org.apache.logging"
    val relocators = listOf(SimpleRelocator(pattern, destination))

    transformer.transform(TransformerContext(PLUGIN_CACHE_FILE, getResourceStream(), relocators))
    assertThat(transformer.hasTransformedResource()).isTrue()

    // Write out to a fake jar file
    val testableZipFile = File.createTempFile("testable-zip-file-", ".jar")
    val zipOutputStream = ZipOutputStream(testableZipFile.outputStream().buffered())
    transformer.modifyOutputStream(zipOutputStream, true)
    zipOutputStream.close()

    // Pull the data back out and make sure it was transformed
    val cache = PluginCache()
    val urlString = "jar:" + testableZipFile.toURI().toURL() + "!/" + PLUGIN_CACHE_FILE
    cache.loadCacheFiles(Collections.enumeration(listOf(URL(urlString))))

    assertThat(cache.getCategory("lookup")["date"]?.className)
      .isEqualTo("new.location.org.apache.logging.log4j.core.lookup.DateLookup")
  }

  private fun getResourceStream(resource: String = PLUGIN_CACHE_FILE): InputStream {
    return this::class.java.classLoader.getResourceAsStream(resource) ?: throw FileNotFoundException("Resource not found: $resource")
  }

  companion object {
    private const val PLUGIN_CACHE_FILE = "META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat"
  }
}
