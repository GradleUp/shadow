package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.ReproducibleProperties
import com.github.jengelman.gradle.plugins.shadow.internal.checkDupStrategy
import com.github.jengelman.gradle.plugins.shadow.internal.zipEntry
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocateClass
import com.github.jengelman.gradle.plugins.shadow.relocation.relocatePath
import java.util.Properties
import kotlin.text.Charsets.ISO_8859_1
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.util.PatternSet

/**
 * A resource transformer that handles Spring Boot configuration files to enable proper merging when
 * creating a shadow JAR.
 *
 * The following Spring Boot resource files are handled:
 * - `META-INF/spring.factories`: Properties file with comma-separated class name values, merged by
 *   appending values with a comma separator.
 * - `META-INF/spring.handlers`: Properties file mapping XML namespace URIs to a single handler
 *   class. Entries are merged by key; conflicting duplicate keys fail the build.
 * - `META-INF/spring.schemas`: Properties file mapping schema URLs to a single classpath resource.
 *   Entries are merged by key; conflicting duplicate keys fail the build.
 * - `META-INF/spring.tooling`: Properties file with tooling metadata, merged by appending with a
 *   comma separator.
 * - `META-INF/spring-autoconfigure-metadata.properties`: Properties file with autoconfiguration
 *   metadata, merged by appending with a comma separator.
 * - `META-INF/spring/?.imports`: Line-based files where each line is a fully qualified class name;
 *   lines are deduplicated and merged across JAR files.
 *
 * Class relocation is applied to both the keys and values of properties files (using path-based
 * relocation for slash-notation values, and class-based relocation for dot-notation values), as
 * well as to each line of `.imports` files.
 *
 * @see <a href="https://github.com/GradleUp/shadow/issues/1489">Issue #1489</a>
 */
@CacheableTransformer
public open class SpringBootTransformer
@JvmOverloads
constructor(
  patternSet: PatternSet =
    PatternSet()
      .include("META-INF/spring.factories")
      .include("META-INF/spring-autoconfigure-metadata.properties")
      .include("META-INF/spring.handlers")
      .include("META-INF/spring.schemas")
      .include("META-INF/spring.tooling")
      .include("META-INF/spring/aot.factories")
      .include("META-INF/spring/*.imports")
) : PatternFilterableResourceTransformer(patternSet = patternSet) {
  private val propertiesEntries = mutableMapOf<String, ReproducibleProperties>()
  private val importsEntries = mutableMapOf<String, LinkedHashSet<String>>()

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return super.canTransformResource(element).also { flag -> checkDupStrategy(flag, element) }
  }

  override fun transform(context: TransformerContext) {
    val path = context.path
    when {
      path.startsWith("META-INF/spring/") && path.endsWith(".imports") -> {
        val entries = importsEntries.getOrPut(path) { linkedSetOf() }
        context.inputStream
          .bufferedReader()
          .lineSequence()
          .map { it.trim() }
          .filter { it.isNotEmpty() && !it.startsWith("#") }
          .map { context.relocators.relocateClass(it) }
          .forEach { entries.add(it) }
      }
      else -> {
        val props = propertiesEntries.getOrPut(path) { ReproducibleProperties() }
        val incoming = Properties().apply { load(context.inputStream.bufferedReader(ISO_8859_1)) }
        incoming.forEach { (rawKey, rawValue) ->
          val key = context.relocators.relocateClass(rawKey as String)
          val value =
            (rawValue as String).splitToSequence(",").joinToString(",") { part ->
              context.relocators.relocateValue(part.trim())
            }
          val existing = props.getProperty(key)
          when {
            existing == null -> props[key] = value
            existing == value -> Unit
            path == "META-INF/spring.handlers" || path == "META-INF/spring.schemas" ->
              error("Conflicting entries in $path for key '$key': '$existing' vs '$value'")
            else -> props[key] = "$existing,$value"
          }
        }
      }
    }
  }

  override fun hasTransformedResource(): Boolean =
    propertiesEntries.isNotEmpty() || importsEntries.isNotEmpty()

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    propertiesEntries.forEach { (path, props) ->
      os.putNextEntry(zipEntry(path, preserveFileTimestamps))
      props.writeWithoutComments(ISO_8859_1, os)
      os.closeEntry()
    }
    importsEntries.forEach { (path, entries) ->
      os.putNextEntry(zipEntry(path, preserveFileTimestamps))
      os.write(entries.joinToString("\n").toByteArray())
      os.closeEntry()
    }
  }

  private companion object {
    /**
     * Relocates a value that may be either a dot-notation class name (e.g., `com.example.MyClass`)
     * or a slash-notation resource path (e.g., `com/example/schema.xsd`). Path-notation values are
     * relocated using [Iterable.relocatePath], and class-notation values using
     * [Iterable.relocateClass].
     */
    fun Iterable<Relocator>.relocateValue(value: String): String {
      return if (value.contains('/')) relocatePath(value) else relocateClass(value)
    }
  }
}
