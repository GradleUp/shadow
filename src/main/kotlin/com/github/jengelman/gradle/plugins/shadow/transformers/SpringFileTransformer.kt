package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.CleanProperties
import com.github.jengelman.gradle.plugins.shadow.internal.inputStream
import com.github.jengelman.gradle.plugins.shadow.internal.property
import com.github.jengelman.gradle.plugins.shadow.internal.zipEntry
import com.github.jengelman.gradle.plugins.shadow.relocation.relocateClass
import java.io.InputStream
import java.nio.charset.Charset
import javax.inject.Inject
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

/**
 * Resources transformer that merges Spring metadata files and applies relocation to class names.
 *
 * Spring has several metadata files that need special handling during shading:
 * - META-INF/spring-autoconfigure-metadata.properties (class values)
 * - META-INF/spring.factories (class values)
 * - META-INF/spring.handlers (class values)
 * - META-INF/spring.schemas (path values)
 * - META-INF/spring.tooling (path values)
 *
 * Files with class values will have their values relocated using the configured relocators.
 * Files with path values will be merged as-is without relocation.
 * All files support comma-separated values that are properly merged.
 *
 * This transformer complements [ServiceFileTransformer] which handles
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports files.
 *
 * @author John Engelman
 */
@CacheableTransformer
public open class SpringFileTransformer @Inject constructor(
  final override val objectFactory: ObjectFactory,
) : ResourceTransformer {
  private inline val charset get() = Charset.forName(charsetName.get())

  @get:Internal
  internal val springEntries = mutableMapOf<String, CleanProperties>()

  @get:Input
  public open val charsetName: Property<String> = objectFactory.property(Charsets.ISO_8859_1.name())

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return SPRING_METADATA_PATHS.any { element.path == it }
  }

  override fun transform(context: TransformerContext) {
    val props = springEntries[context.path]
    val incoming = loadProperties(context.inputStream)
    
    if (props == null) {
      // First time seeing this file
      springEntries[context.path] = applyRelocation(incoming, context)
    } else {
      // Merge with existing properties
      val relocatedIncoming = applyRelocation(incoming, context)
      for ((key, value) in relocatedIncoming) {
        if (props.containsKey(key)) {
          // Merge comma-separated values
          val existingValue = props.getProperty(key as String)
          props[key] = "$existingValue,$value"
        } else {
          props[key] = value
        }
      }
    }
  }

  private fun loadProperties(inputStream: InputStream): CleanProperties {
    val props = CleanProperties()
    // InputStream closed by caller, so we don't do it here.
    props.load(inputStream.bufferedReader(charset))
    return props
  }

  private fun applyRelocation(properties: CleanProperties, context: TransformerContext): CleanProperties {
    val result = CleanProperties()
    val isClassValueFile = CLASS_VALUE_FILES.any { context.path.endsWith(it) }
    
    properties.forEach { (key, value) ->
      if (isClassValueFile && value is String) {
        // Apply relocation to comma-separated class names
        val relocatedValue = value.split(",")
          .map { className ->
            val trimmed = className.trim()
            context.relocators.fold(trimmed) { acc, relocator ->
              if (relocator.canRelocateClass(acc)) {
                relocator.relocateClass(acc)
              } else {
                acc
              }
            }
          }
          .joinToString(",")
        result[key] = relocatedValue
      } else {
        // Path values or non-class values - no relocation
        result[key] = value
      }
    }
    return result
  }

  override fun hasTransformedResource(): Boolean {
    return springEntries.isNotEmpty()
  }

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    // Cannot close the writer as the OutputStream needs to remain open.
    val zipWriter = os.writer(charset)
    springEntries.forEach { (path, props) ->
      os.putNextEntry(zipEntry(path, preserveFileTimestamps))
      props.inputStream(charset).bufferedReader(charset).use {
        it.copyTo(zipWriter)
      }
      zipWriter.flush()
      os.closeEntry()
    }
  }

  private companion object {
    private val SPRING_METADATA_PATHS = setOf(
      "META-INF/spring-autoconfigure-metadata.properties",
      "META-INF/spring.factories",
      "META-INF/spring.handlers",
      "META-INF/spring.schemas",
      "META-INF/spring.tooling"
    )

    private val CLASS_VALUE_FILES = setOf(
      "spring-autoconfigure-metadata.properties",
      "spring.factories",
      "spring.handlers"
    )
  }
}