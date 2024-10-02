

package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.CleanProperties
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.Properties
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

/**
 * Resources transformer that merges Properties files.
 *
 * <p>The default merge strategy discards duplicate values coming from additional
 * resources. This behavior can be changed by setting a value for the <tt>mergeStrategy</tt>
 * property, such as 'first' (default), 'latest' or 'append'. If the merge strategy is
 * 'latest' then the last value of a matching property entry will be used. If the
 * merge strategy is 'append' then the property values will be combined, using a
 * merge separator (default value is ','). The merge separator can be changed by
 * setting a value for the <tt>mergeSeparator</tt> property.</p>
 *
 * Say there are two properties files A and B with the
 * following entries:
 *
 * <strong>A</strong>
 * <ul>
 *   <li>key1 = value1</li>
 *   <li>key2 = value2</li>
 * </ul>
 *
 * <strong>B</strong>
 * <ul>
 *   <li>key2 = balue2</li>
 *   <li>key3 = value3</li>
 * </ul>
 *
 * With <tt>mergeStrategy = first</tt> you get
 *
 * <strong>C</strong>
 * <ul>
 *   <li>key1 = value1</li>
 *   <li>key2 = value2</li>
 *   <li>key3 = value3</li>
 * </ul>
 *
 * With <tt>mergeStrategy = latest</tt> you get
 *
 * <strong>C</strong>
 * <ul>
 *   <li>key1 = value1</li>
 *   <li>key2 = balue2</li>
 *   <li>key3 = value3</li>
 * </ul>
 *
 * With <tt>mergeStrategy = append</tt> and <tt>mergeSparator = ;</tt> you get
 *
 * <strong>C</strong>
 * <ul>
 *   <li>key1 = value1</li>
 *   <li>key2 = value2;balue2</li>
 *   <li>key3 = value3</li>
 * </ul>
 *
 * <p>There are three additional properties that can be set: <tt>paths</tt>, <tt>mappings</tt>,
 * and <tt>keyTransformer</tt>.
 * The first contains a list of strings or regexes that will be used to determine if
 * a path should be transformed or not. The merge strategy and merge separator are
 * taken from the global settings.</p>
 *
 * <p>The <tt>mappings</tt> property allows you to define merge strategy and separator per
 * path</p>. If either <tt>paths</tt> or <tt>mappings</tt> is defined then no other path
 * entries will be merged. <tt>mappings</tt> has precedence over <tt>paths</tt> if both
 * are defined.</p>
 *
 * <p>If you need to transform keys in properties files, e.g. because they contain class
 * names about to be relocated, you can set the <tt>keyTransformer</tt> property to a
 * closure that receives the original key and returns the key name to be used.</p>
 *
 * <p>Example:</p>
 * <pre>
 * import org.codehaus.griffon.gradle.shadow.transformers.*
 * tasks.named('shadowJar', ShadowJar) {
 *     transform(PropertiesFileTransformer) {
 *         paths = [
 *             'META-INF/editors/java.beans.PropertyEditor'
 *         ]
 *         keyTransformer = { key ->
 *             key.replaceAll('^(orig\.package\..*)$', 'new.prefix.$1')
 *         }
 *     }
 * }
 * </pre>
 */
public class PropertiesFileTransformer : Transformer {
  private val propertiesEntries = mutableMapOf<String, CleanProperties>()

  @Input
  public var paths: List<String> = listOf()

  @Input
  public var mappings: Map<String, Map<String, String>> = mapOf()

  /**
   * latest, append
   */
  @Input
  public var mergeStrategy: String = "first"

  @Input
  public var mergeSeparator: String = ","

  @Input
  public var charset: Charset = Charsets.ISO_8859_1

  @Internal
  public var keyTransformer: (String) -> String = defaultKeyTransformer

  override fun canTransformResource(element: FileTreeElement): Boolean {
    val path = element.relativePath.pathString
    if (mappings.containsKey(path)) return true
    for (key in mappings.keys) {
      if (path.matches(key.toRegex())) return true
    }

    if (path in paths) return true
    for (p in paths) {
      if (path.matches(p.toRegex())) return true
    }

    return mappings.isEmpty() && paths.isEmpty() && path.endsWith(PROPERTIES_SUFFIX)
  }

  override fun transform(context: TransformerContext) {
    val props = propertiesEntries[context.path]
    val incoming = loadAndTransformKeys(context.inputStream)
    if (props == null) {
      propertiesEntries[context.path] = incoming
    } else {
      incoming.forEach { (key, value) ->
        if (props.containsKey(key)) {
          when (mergeStrategyFor(context.path).lowercase()) {
            "latest" -> props[key] = value
            "append" -> props[key] = props.getProperty(key.toString()) + mergeSeparatorFor(context.path) + value
            "first" -> Unit // continue
            else -> Unit // continue
          }
        } else {
          props[key] = value
        }
      }
    }
  }

  private fun loadAndTransformKeys(inputStream: InputStream?): CleanProperties {
    val props = CleanProperties()
    inputStream?.let {
      props.load(it.reader(charset))
    }
    return transformKeys(props) as CleanProperties
  }

  private fun transformKeys(properties: Properties): Properties {
    if (keyTransformer == defaultKeyTransformer) return properties
    val result = CleanProperties()
    properties.forEach { (key, value) ->
      result[keyTransformer(key as String)] = value
    }
    return result
  }

  private fun mergeStrategyFor(path: String): String {
    mappings[path]?.let {
      return it["mergeStrategy"] ?: mergeStrategy
    }
    for (key in mappings.keys) {
      if (path.matches(key.toRegex())) {
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
      if (path.matches(Regex(key))) {
        return mappings[key]?.get("mergeSeparator") ?: mergeSeparator
      }
    }
    return mergeSeparator
  }

  override fun hasTransformedResource(): Boolean = propertiesEntries.isNotEmpty()

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    val zipWriter = os.writer(charset)
    propertiesEntries.forEach { (path, props) ->
      val entry = ZipEntry(path)
      entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
      os.putNextEntry(entry)
      readerFor(props, charset).copyTo(zipWriter)
      zipWriter.flush()
      os.closeEntry()
    }
  }

  private fun readerFor(props: Properties, charset: Charset): InputStreamReader {
    val baos = ByteArrayOutputStream()
    baos.writer(charset).use { writer ->
      props.store(writer, "")
    }
    return baos.toByteArray().inputStream().reader(charset)
  }

  private companion object {
    const val PROPERTIES_SUFFIX = ".properties"
    val defaultKeyTransformer: (String) -> String = { it }
  }
}
