package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.CleanProperties
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.Properties
import java.util.function.Function
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

/**
 * Resources transformer that merges Properties files.
 *
 * The default merge strategy discards duplicate values coming from additional
 * resources. This behavior can be changed by setting a value for the `mergeStrategy`
 * property, such as 'first' (default), 'latest' or 'append'. If the merge strategy is
 * 'latest' then the last value of a matching property entry will be used. If the
 * merge strategy is 'append' then the property values will be combined, using a
 * merge separator (default value is ','). The merge separator can be changed by
 * setting a value for the `mergeSeparator` property.
 *
 * Say there are two properties files A and B with the
 * following entries:
 *
 * **A**
 * - key1 = value1
 * - key2 = value2
 *
 * **B**
 * - key2 = balue2
 * - key3 = value3
 *
 * With `mergeStrategy = first` you get
 *
 * **C**
 * - key1 = value1
 * - key2 = value2
 * - key3 = value3
 *
 * With `mergeStrategy = latest` you get
 *
 * **C**
 * - key1 = value1
 * - key2 = balue2
 * - key3 = value3
 *
 * With `mergeStrategy = append` and `mergeSeparator = ;` you get
 *
 * **C**
 * - key1 = value1
 * - key2 = value2;balue2
 * - key3 = value3
 *
 * There are three additional properties that can be set: `paths`, `mappings`,
 * and `keyTransformer`.
 * The first contains a list of strings or regexes that will be used to determine if
 * a path should be transformed or not. The merge strategy and merge separator are
 * taken from the global settings.
 *
 * The `mappings` property allows you to define merge strategy and separator per
 * path. If either `paths` or `mappings` is defined then no other path
 * entries will be merged. `mappings` has precedence over `paths` if both
 * are defined.
 *
 * If you need to transform keys in properties files, e.g. because they contain class
 * names about to be relocated, you can set the `keyTransformer` property to a
 * closure that receives the original key and returns the key name to be used.
 *
 * Example:
 * ```groovy
 * import org.codehaus.griffon.gradle.shadow.transformers.*
 * tasks.named('shadowJar', ShadowJar) {
 *   transform(PropertiesFileTransformer) {
 *     paths = [
 *       'META-INF/editors/java.beans.PropertyEditor'
 *     ]
 *     keyTransformer = { key ->
 *       key.replaceAll('^(orig\.package\..*)$', 'new.prefix.$1')
 *     }
 *   }
 * }
 * ```
 *
 * @author Andres Almiray
 * @author Marc Philipp
 */
public open class PropertiesFileTransformer : Transformer {
  private val propertiesEntries = mutableMapOf<String, CleanProperties>()
  private val _charset get() = Charset.forName(charset)

  @get:Input
  public var paths: List<String> = listOf()

  @get:Input
  public var mappings: Map<String, Map<String, String>> = mapOf()

  /**
   * Optional values: first, latest, append.
   */
  @get:Input
  public var mergeStrategy: String = "first"

  @get:Input
  public var mergeSeparator: String = ","

  @get:Input
  public var charset: String = "ISO_8859_1"

  /**
   * Use [java.util.function.Function] here for compatibility with Groovy and Java.
   */
  @get:Internal
  public var keyTransformer: Function<String, String> = IDENTITY

  override fun canTransformResource(element: FileTreeElement): Boolean {
    val path = element.relativePath.pathString
    if (path in mappings) return true
    for (key in mappings.keys) {
      if (key.toRegex().containsMatchIn(path)) return true
    }
    if (path in paths) return true
    for (p in paths) {
      if (p.toRegex().containsMatchIn(path)) return true
    }
    return mappings.isEmpty() && paths.isEmpty() && path.endsWith(PROPERTIES_SUFFIX)
  }

  override fun transform(context: TransformerContext) {
    val props = propertiesEntries[context.path]
    val incoming = loadAndTransformKeys(context.inputStream)
    if (props == null) {
      propertiesEntries[context.path] = incoming
    } else {
      for ((key, value) in incoming) {
        if (props.containsKey(key)) {
          when (mergeStrategyFor(context.path).lowercase()) {
            "latest" -> props[key] = value
            "append" -> props[key] = props.getProperty(key as String) + mergeSeparatorFor(context.path) + value
            "first" -> Unit
            else -> Unit
          }
        } else {
          props[key] = value
        }
      }
    }
  }

  private fun loadAndTransformKeys(inputStream: InputStream): CleanProperties {
    val props = CleanProperties()
    // InputStream closed by caller, so we don't do it here.
    props.load(inputStream.reader(_charset))
    return transformKeys(props)
  }

  private fun transformKeys(properties: Properties): CleanProperties {
    if (keyTransformer === IDENTITY) {
      return properties as CleanProperties
    }
    val result = CleanProperties()
    properties.forEach { (key, value) ->
      result[keyTransformer.apply(key as String)] = value
    }
    return result
  }

  private fun mergeStrategyFor(path: String): String {
    mappings[path]?.let {
      return it["mergeStrategy"] ?: mergeStrategy
    }
    for (key in mappings.keys) {
      if (key.toRegex().containsMatchIn(path)) {
        return mappings[key]?.get("mergeStrategy") ?: mergeStrategy
      }
    }
    return mergeStrategy
  }

  private fun mergeSeparatorFor(path: String): String {
    mappings[path]?.let {
      return it["mergeSeparator"] ?: mergeSeparator
    }
    for (key in mappings.keys) {
      if (key.toRegex().containsMatchIn(path)) {
        return mappings[key]?.get("mergeSeparator") ?: mergeSeparator
      }
    }
    return mergeSeparator
  }

  override fun hasTransformedResource(): Boolean {
    return propertiesEntries.isNotEmpty()
  }

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    // cannot close the writer as the OutputStream needs to remain open
    val zipWriter = os.writer(_charset)
    propertiesEntries.forEach { (path, props) ->
      val entry = ZipEntry(path)
      entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
      os.putNextEntry(entry)
      props.toReader().use {
        it.copyTo(zipWriter)
      }
      zipWriter.flush()
      os.closeEntry()
    }
  }

  private fun Properties.toReader(): InputStreamReader {
    val os = ByteArrayOutputStream()
    os.writer(Charset.forName(charset)).use { writer ->
      store(writer, "")
    }
    return os.toByteArray().inputStream().reader(_charset)
  }

  private companion object {
    private const val PROPERTIES_SUFFIX = ".properties"
    private val IDENTITY = Function<String, String> { it }
  }
}
