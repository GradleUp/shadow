package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext.Companion.getEntryTimestamp
import java.io.File
import java.net.URL
import java.util.Collections
import java.util.Enumeration
import org.apache.commons.io.output.CloseShieldOutputStream
import org.apache.logging.log4j.core.config.plugins.processor.PluginCache
import org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement

/**
 * Modified from the maven equivalent to work with gradle
 *
 * Modified from [org.apache.logging.log4j.maven.plugins.shade.transformer.Log4j2PluginCacheFileTransformer.java](https://github.com/apache/logging-log4j-transform/blob/main/log4j-transform-maven-shade-plugin-extensions/src/main/java/org/apache/logging/log4j/maven/plugins/shade/transformer/Log4j2PluginCacheFileTransformer.java).
 *
 * @author Paul Nelson Baker
 * @author John Engelman
 */
@CacheableTransformer
public open class Log4j2PluginsCacheFileTransformer : Transformer {
  private val temporaryFiles = mutableListOf<File>()
  private val relocators = mutableListOf<Relocator>()
  private var stats: ShadowStats? = null

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return PluginProcessor.PLUGIN_CACHE_FILE == element.name
  }

  override fun transform(context: TransformerContext) {
    val temporaryFile = File.createTempFile("Log4j2Plugins", ".dat")
    temporaryFile.deleteOnExit()
    temporaryFiles.add(temporaryFile)
    val fos = temporaryFile.outputStream()
    context.inputStream.use {
      it.copyTo(fos)
    }

    relocators.addAll(context.relocators)

    if (stats == null) {
      stats = context.stats
    }
  }

  override fun hasTransformedResource(): Boolean {
    // This functionality matches the original plugin, however, I'm not clear what
    // the exact logic is. From what I can tell temporaryFiles should be never be empty
    // if anything has been performed.
    return temporaryFiles.isNotEmpty() || relocators.isNotEmpty()
  }

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    val pluginCache = PluginCache()
    pluginCache.loadCacheFiles(urlEnumeration)
    relocatePlugins(pluginCache)
    val entry = ZipEntry(PluginProcessor.PLUGIN_CACHE_FILE)
    entry.time = getEntryTimestamp(preserveFileTimestamps, entry.time)
    os.putNextEntry(entry)
    pluginCache.writeCache(CloseShieldOutputStream.wrap(os))
    temporaryFiles.clear()
  }

  private fun relocatePlugins(pluginCache: PluginCache) {
    pluginCache.allCategories.values.forEach { currentMap ->
      currentMap.values.forEach { currentPluginEntry ->
        val className = currentPluginEntry.className
        val relocateClassContext = RelocateClassContext(className, requireNotNull(stats))
        relocators.firstOrNull { it.canRelocateClass(className) }?.let { relocator ->
          // Then we perform that relocation and update the plugin entry to reflect the new value.
          currentPluginEntry.className = relocator.relocateClass(relocateClassContext)
        }
      }
    }
  }

  private val urlEnumeration: Enumeration<URL>
    get() {
      val urls = temporaryFiles.map { it.toURI().toURL() }
      return Collections.enumeration(urls)
    }
}
