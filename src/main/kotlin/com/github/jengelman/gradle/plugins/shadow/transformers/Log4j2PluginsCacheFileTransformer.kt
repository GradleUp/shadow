package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.checkDupStrategy
import com.github.jengelman.gradle.plugins.shadow.internal.writeEntry
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocateClass
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Locale
import java.util.SortedMap
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement

/**
 * Merges Log4j 2 plugin cache files (`Log4j2Plugins.dat`) and relocates the plugin classes in them.
 *
 * The binary format is read and written directly instead of depending on `log4j-core`, mirroring
 * [org.apache.logging.log4j.core.config.plugins.processor.PluginCache](https://github.com/apache/logging-log4j2/blob/2.x/log4j-core/src/main/java/org/apache/logging/log4j/core/config/plugins/processor/PluginCache.java).
 * The format is frozen in Log4j 2. Log4j 3 no longer generates it, but still reads it for plugins
 * compiled against Log4j 2, see
 * [org.apache.logging.log4j.plugins.model.PluginRegistry](https://github.com/apache/logging-log4j2/blob/main/log4j-plugins/src/main/java/org/apache/logging/log4j/plugins/model/PluginRegistry.java).
 * Log4j 3 plugins are registered as
 * `META-INF/services/org.apache.logging.log4j.plugins.model.PluginService` instead, which can be
 * merged by [ServiceFileTransformer].
 *
 * Modified from
 * [org.apache.logging.log4j.maven.plugins.shade.transformer.Log4j2PluginCacheFileTransformer.java](https://github.com/apache/logging-log4j-transform/blob/main/log4j-transform-maven-shade-plugin-extensions/src/main/java/org/apache/logging/log4j/maven/plugins/shade/transformer/Log4j2PluginCacheFileTransformer.java).
 *
 * @author Paul Nelson Baker
 * @author John Engelman
 */
@CacheableTransformer
public open class Log4j2PluginsCacheFileTransformer : ResourceTransformer {
  /** Plugin entries keyed by category and then by plugin key, sorted like Log4j's `PluginCache`. */
  private val categories = sortedMapOf<String, SortedMap<String, PluginEntry>>()

  /** [Relocator] instances to share across the transformation stages. */
  private val tempRelocators = mutableListOf<Relocator>()

  /** Whether any dat file is collected, even if it contains no plugins. */
  private var hasDatFile = false

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return (PLUGIN_CACHE_FILE == element.path).also { flag -> checkDupStrategy(flag, element) }
  }

  override fun transform(context: TransformerContext) {
    // Don't close the input stream, it is managed by the caller.
    val input = DataInputStream(context.inputStream.buffered())
    repeat(input.readInt()) {
      val category = categories.getOrPut(input.readUTF().lowercase(Locale.ROOT)) { sortedMapOf() }
      repeat(input.readInt()) {
        // Always read all parts of the entry, even if not adding, to advance the stream.
        val entry =
          PluginEntry(
            key = input.readUTF(),
            className = input.readUTF(),
            name = input.readUTF(),
            printable = input.readBoolean(),
            defer = input.readBoolean(),
          )
        // The first found entry wins, same as Log4j's `PluginCache`.
        category.putIfAbsent(entry.key, entry)
      }
    }
    tempRelocators.addAll(context.relocators)
    hasDatFile = true
  }

  /** @return `true` if any dat file collected. */
  override fun hasTransformedResource(): Boolean = hasDatFile

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    os.writeEntry(PLUGIN_CACHE_FILE, preserveFileTimestamps) {
      // Don't close the output stream, it would close the jar output.
      val output = DataOutputStream(buffered())
      output.writeInt(categories.size)
      categories.forEach { (category, entries) ->
        output.writeUTF(category)
        output.writeInt(entries.size)
        entries.values.forEach { entry ->
          output.writeUTF(entry.key)
          output.writeUTF(tempRelocators.relocateClass(entry.className))
          output.writeUTF(entry.name)
          output.writeBoolean(entry.printable)
          output.writeBoolean(entry.defer)
        }
      }
      output.flush()
    }
    categories.clear()
    hasDatFile = false
  }

  private class PluginEntry(
    val key: String,
    val className: String,
    val name: String,
    val printable: Boolean,
    val defer: Boolean,
  )

  internal companion object {
    const val PLUGIN_CACHE_FILE: String =
      "META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat"
  }
}
