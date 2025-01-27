package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.CleanProperties
import com.github.jengelman.gradle.plugins.shadow.internal.inputStream
import com.github.jengelman.gradle.plugins.shadow.internal.mapProperty
import com.github.jengelman.gradle.plugins.shadow.internal.property
import com.github.jengelman.gradle.plugins.shadow.internal.setProperty
import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer.MergeStrategy
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Properties
import java.util.function.Function as JavaFunction
import javax.inject.Inject
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

/**
 * Resources transformer that merges Properties files.
 *
 * The default merge strategy discards duplicate values coming from additional
 * resources. This behavior can be changed by setting a value for the [mergeStrategy] property,
 * such as [MergeStrategy.First] (default), [MergeStrategy.Latest] or [MergeStrategy.Append]. If the merge strategy is
 * [MergeStrategy.Latest] then the last value of a matching property entry will be used. If the
 * merge strategy is [MergeStrategy.Append] then the property values will be combined, using a
 * merge separator (default value is ','). The merge separator can be changed by
 * setting a value for the [mergeSeparator] property.
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
 * With `mergeStrategy = MergeStrategy.First` you get
 *
 * **C**
 * - key1 = value1
 * - key2 = value2
 * - key3 = value3
 *
 * With `mergeStrategy = MergeStrategy.Latest` you get
 *
 * **C**
 * - key1 = value1
 * - key2 = balue2
 * - key3 = value3
 *
 * With `mergeStrategy = MergeStrategy.Append` and `mergeSeparator = ;` you get
 *
 * **C**
 * - key1 = value1
 * - key2 = value2;balue2
 * - key3 = value3
 *
 * There are three additional properties that can be set: [paths], [mappings],
 * and [keyTransformer].
 * The first contains a list of strings or regexes that will be used to determine if
 * a path should be transformed or not. The merge strategy and merge separator are
 * taken from the global settings.
 *
 * The [mappings] property allows you to define merge strategy and separator per
 * path. If either [paths] or [mappings] is defined then no other path
 * entries will be merged. [mappings] has precedence over [paths] if both
 * are defined.
 *
 * If you need to transform keys in properties files, e.g. because they contain class
 * names about to be relocated, you can set the [keyTransformer] property to a
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
 * Related to [org.apache.maven.plugins.shade.resource.properties.PropertiesTransformer.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/resource/properties/PropertiesTransformer.java).
 *
 * @author Andres Almiray
 * @author Marc Philipp
 */
public open class PropertiesFileTransformer @Inject constructor(
  final override val objectFactory: ObjectFactory,
) : Transformer {
  private inline val charset get() = Charset.forName(charsetName.get())

  @get:Internal
  internal val propertiesEntries = mutableMapOf<String, CleanProperties>()

  @get:Input
  public open val paths: SetProperty<String> = objectFactory.setProperty()

  @get:Input
  public open val mappings: MapProperty<String, Map<String, String>> = objectFactory.mapProperty()

  @get:Input
  public open val mergeStrategy: Property<MergeStrategy> = objectFactory.property(MergeStrategy.First)

  @get:Input
  public open val mergeSeparator: Property<String> = objectFactory.property(",")

  @get:Input
  public open val charsetName: Property<String> = objectFactory.property(Charsets.ISO_8859_1.name())

  @get:Internal
  public open val keyTransformer: Property<JavaFunction<String, String>> = objectFactory.property(IDENTITY)

  override fun canTransformResource(element: FileTreeElement): Boolean {
    val mappings = mappings.get()
    val paths = paths.get()

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
          when (MergeStrategy.from(mergeStrategyFor(context.path))) {
            MergeStrategy.Latest -> {
              props[key] = value
            }
            MergeStrategy.Append -> {
              props[key] = props.getProperty(key as String) + mergeSeparatorFor(context.path) + value
            }
            MergeStrategy.First -> Unit
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
    props.load(inputStream.bufferedReader(charset))
    return transformKeys(props)
  }

  private fun transformKeys(properties: Properties): CleanProperties {
    val keyTransformer = keyTransformer.get()

    if (keyTransformer == IDENTITY) {
      return properties as CleanProperties
    }
    val result = CleanProperties()
    properties.forEach { (key, value) ->
      result[keyTransformer.apply(key as String)] = value
    }
    return result
  }

  private fun mergeStrategyFor(path: String): String {
    val mappings = mappings.get()
    val mergeStrategy = mergeStrategy.get().name

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
    val mappings = mappings.get()
    val mergeSeparator = mergeSeparator.get()

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
    val zipWriter = os.writer(charset)
    propertiesEntries.forEach { (path, props) ->
      val entry = ZipEntry(path)
      entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
      os.putNextEntry(entry)
      props.inputStream(charset).bufferedReader(charset).use {
        it.copyTo(zipWriter)
      }
      zipWriter.flush()
      os.closeEntry()
    }
  }

  public enum class MergeStrategy {
    First,
    Latest,
    Append,
    ;

    public companion object {
      @JvmStatic
      public fun from(value: String): MergeStrategy {
        @OptIn(ExperimentalStdlibApi::class)
        return entries.find { it.name.equals(value, ignoreCase = true) }
          ?: throw IllegalArgumentException("Unknown merge strategy: $value")
      }
    }
  }

  private companion object {
    private const val PROPERTIES_SUFFIX = ".properties"
    private val IDENTITY = JavaFunction.identity<String>()
  }
}
