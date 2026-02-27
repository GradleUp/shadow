package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.ReproducibleProperties
import com.github.jengelman.gradle.plugins.shadow.internal.zipEntry
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocateClass
import com.github.jengelman.gradle.plugins.shadow.relocation.relocatePath
import java.util.Properties
import javax.inject.Inject
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Internal

/**
 * A resource transformer that handles Spring Boot configuration files to enable proper merging when
 * creating a shadow JAR.
 *
 * The following Spring Boot resource files are handled:
 * - `META-INF/spring.factories`: Properties file with comma-separated class name values, merged by
 *   appending values with a comma separator.
 * - `META-INF/spring.handlers`: Properties file with class name values, merged by appending with a
 *   comma separator.
 * - `META-INF/spring.schemas`: Properties file with schema URL-to-path mappings, merged by
 *   appending with a comma separator.
 * - `META-INF/spring.tooling`: Properties file with tooling metadata, merged by appending with a
 *   comma separator.
 * - `META-INF/spring-autoconfigure-metadata.properties`: Properties file with autoconfiguration
 *   metadata, merged by appending with a comma separator.
 * - `META-INF/spring/`*`.imports`: Line-based files where each line is a fully qualified class
 *   name; lines are deduplicated and merged across JAR files.
 *
 * Class relocation is applied to both the keys and values of properties files (using path-based
 * relocation for slash-notation values, and class-based relocation for dot-notation values), as
 * well as to each line of `.imports` files.
 *
 * @see <a href="https://github.com/GradleUp/shadow/issues/1489">Issue #1489</a>
 */
@CacheableTransformer
public open class SpringBootTransformer
@Inject
constructor(final override val objectFactory: ObjectFactory) : ResourceTransformer {
  @get:Internal internal val propertiesEntries = mutableMapOf<String, ReproducibleProperties>()

  @get:Internal internal val importsEntries = mutableMapOf<String, LinkedHashSet<String>>()

  override fun canTransformResource(element: FileTreeElement): Boolean {
    val path = element.path
    return path in PROPERTIES_PATHS || isImportsFile(path)
  }

  override fun transform(context: TransformerContext) {
    val path = context.path
    if (isImportsFile(path)) {
      val entries = importsEntries.getOrPut(path) { linkedSetOf() }
      context.inputStream
        .bufferedReader()
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { context.relocators.relocateClass(it) }
        .forEach { entries.add(it) }
    } else {
      val props = propertiesEntries.getOrPut(path) { ReproducibleProperties() }
      val incoming =
        Properties().apply { load(context.inputStream.bufferedReader(PROPERTIES_CHARSET)) }
      incoming.forEach { rawKey, rawValue ->
        val key = context.relocators.relocateClass(rawKey as String)
        val value =
          (rawValue as String).splitToSequence(",").joinToString(",") { part ->
            context.relocators.relocateValue(part.trim())
          }
        val existing = props.getProperty(key)
        if (existing != null) {
          props[key] = "$existing,$value"
        } else {
          props[key] = value
        }
      }
    }
  }

  override fun hasTransformedResource(): Boolean =
    propertiesEntries.isNotEmpty() || importsEntries.isNotEmpty()

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    propertiesEntries.forEach { (path, props) ->
      os.putNextEntry(zipEntry(path, preserveFileTimestamps))
      props.writeWithoutComments(PROPERTIES_CHARSET, os)
      os.closeEntry()
    }
    importsEntries.forEach { (path, entries) ->
      os.putNextEntry(zipEntry(path, preserveFileTimestamps))
      os.write(entries.joinToString("\n").toByteArray())
      os.closeEntry()
    }
  }

  public companion object {
    public const val PATH_SPRING_FACTORIES: String = "META-INF/spring.factories"
    public const val PATH_SPRING_HANDLERS: String = "META-INF/spring.handlers"
    public const val PATH_SPRING_SCHEMAS: String = "META-INF/spring.schemas"
    public const val PATH_SPRING_TOOLING: String = "META-INF/spring.tooling"
    public const val PATH_SPRING_AUTOCONFIGURE_METADATA: String =
      "META-INF/spring-autoconfigure-metadata.properties"

    private const val SPRING_IMPORTS_PREFIX = "META-INF/spring/"

    private val PROPERTIES_CHARSET = Charsets.ISO_8859_1

    internal val PROPERTIES_PATHS =
      setOf(
        PATH_SPRING_FACTORIES,
        PATH_SPRING_HANDLERS,
        PATH_SPRING_SCHEMAS,
        PATH_SPRING_TOOLING,
        PATH_SPRING_AUTOCONFIGURE_METADATA,
      )

    internal fun isImportsFile(path: String): Boolean =
      path.startsWith(SPRING_IMPORTS_PREFIX) && path.endsWith(".imports")

    /**
     * Relocates a value that may be either a dot-notation class name (e.g., `com.example.MyClass`)
     * or a slash-notation resource path (e.g., `com/example/schema.xsd`). Path-notation values are
     * relocated using [Iterable.relocatePath], and class-notation values using
     * [Iterable.relocateClass].
     */
    private fun Iterable<Relocator>.relocateValue(value: String): String {
      return if (value.contains('/')) relocatePath(value) else relocateClass(value)
    }
  }
}
