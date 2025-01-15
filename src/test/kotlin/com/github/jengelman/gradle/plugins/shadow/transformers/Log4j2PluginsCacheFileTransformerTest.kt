package com.github.jengelman.gradle.plugins.shadow.transformers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.github.jengelman.gradle.plugins.shadow.internal.requireResourceAsStream
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.util.SimpleRelocator
import java.io.File
import java.net.URI
import java.util.Collections
import org.apache.logging.log4j.core.config.plugins.processor.PluginCache
import org.apache.tools.zip.ZipOutputStream
import org.junit.jupiter.api.Test

class Log4j2PluginsCacheFileTransformerTest : BaseTransformerTest<Log4j2PluginsCacheFileTransformer>() {
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

  private fun context(vararg relocator: SimpleRelocator): TransformerContext {
    return TransformerContext(PLUGIN_CACHE_FILE, requireResourceAsStream(PLUGIN_CACHE_FILE), relocator.toSet())
  }

  private companion object {
    const val PLUGIN_CACHE_FILE = "META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat"
  }
}
