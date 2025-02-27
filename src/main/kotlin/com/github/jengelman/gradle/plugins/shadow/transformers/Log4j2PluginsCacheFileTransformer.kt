package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.zipEntry
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import java.net.URL
import java.nio.file.Path
import java.util.Collections
import java.util.Enumeration
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream
import org.apache.commons.io.output.CloseShieldOutputStream
import org.apache.logging.log4j.core.config.plugins.processor.PluginCache
import org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor.PLUGIN_CACHE_FILE
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement

/**
 * Modified from [org.apache.logging.log4j.maven.plugins.shade.transformer.Log4j2PluginCacheFileTransformer.java](https://github.com/apache/logging-log4j-transform/blob/main/log4j-transform-maven-shade-plugin-extensions/src/main/java/org/apache/logging/log4j/maven/plugins/shade/transformer/Log4j2PluginCacheFileTransformer.java).
 *
 * @author Paul Nelson Baker
 * @author John Engelman
 */
@CacheableTransformer
public open class Log4j2PluginsCacheFileTransformer : ResourceTransformer {
  /**
   * Log4j config files to share across the transformation stages.
   */
  private val tempFiles = mutableListOf<Path>()

  /**
   * [Relocator] instances to share across the transformation stages.
   */
  private val tempRelocators = mutableListOf<Relocator>()

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return PLUGIN_CACHE_FILE == element.path
  }

  override fun transform(context: TransformerContext) {
    val temporaryFile = createTempFile("Log4j2Plugins", ".dat")
    tempFiles.add(temporaryFile)
    context.inputStream.copyTo(temporaryFile.outputStream())
    tempRelocators.addAll(context.relocators)
  }

  /**
   * @return `true` if any dat file collected.
   */
  override fun hasTransformedResource(): Boolean {
    return tempFiles.isNotEmpty()
  }

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    try {
      val aggregator = PluginCache()
      aggregator.loadCacheFiles(urlEnumeration)
      relocatePlugins(aggregator)
      os.putNextEntry(zipEntry(PLUGIN_CACHE_FILE, preserveFileTimestamps))
      // Prevent the aggregator to close the jar output.
      aggregator.writeCache(CloseShieldOutputStream.wrap(os))
    } finally {
      deleteTempFiles()
    }
  }

  internal fun relocatePlugins(pluginCache: PluginCache) {
    pluginCache.allCategories.values.forEach { currentMap ->
      currentMap.values.forEach { currentPluginEntry ->
        val className = currentPluginEntry.className
        val relocateClassContext = RelocateClassContext(className)
        tempRelocators.firstOrNull { it.canRelocateClass(className) }?.let { relocator ->
          // Then we perform that relocation and update the plugin entry to reflect the new value.
          currentPluginEntry.className = relocator.relocateClass(relocateClassContext)
        }
      }
    }
  }

  private fun deleteTempFiles() {
    val pathIterator = tempFiles.listIterator()
    while (pathIterator.hasNext()) {
      val path = pathIterator.next()
      path.deleteIfExists()
      pathIterator.remove()
    }
  }

  private val urlEnumeration: Enumeration<URL>
    get() {
      val urls = tempFiles.map { it.toUri().toURL() }
      return Collections.enumeration(urls)
    }
}
