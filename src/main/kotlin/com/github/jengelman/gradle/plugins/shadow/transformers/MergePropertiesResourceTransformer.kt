package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.WithPatternFilterable
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.LinkedHashSet
import java.util.Properties
import javax.inject.Inject
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.gradle.api.file.FileTreeElement
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.util.PatternFilterable
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Transformer to merge the contents of equally named property files. The set of property files to
 * merge is determined by the implemented [PatternFilterable].
 *
 * Conflicting property values from different property files are detected and lead to an error. Use
 * the [ignoreDuplicates] property to specify path patterns for property files that should skip the
 * conflicting values check.
 */
@CacheableTransformer
public open class MergePropertiesResourceTransformer @Inject constructor(objectFactory: ObjectFactory) :
  WithPatternFilterable(),
  ResourceTransformer {

  private data class PropertiesFile(
    val ignoreConflicts: Boolean,
    val content: StringBuilder = StringBuilder(),
    val properties: MutableMap<Any, Any> = LinkedHashMap(),
    val sources: MutableList<File> = ArrayList(),
    val conflicts: MutableMap<Any, MutableSet<Any>> = LinkedHashMap(),
  )

  /**
   * Flag whether the transformer should *not* fail on duplicate property values. Defaults to
   * `false`.
   */
  @get:Input public val dontFail: Property<Boolean> = objectFactory.property(Boolean::class.java).value(false)

  private val ignoreDuplicatesFor = WithPatternFilterable(canBeEmpty = true)

  /** Patterns for property files that should skip the conflicting values check. */
  @get:Nested public val ignoreDuplicates: PatternFilterable = ignoreDuplicatesFor

  private val propertiesFiles: MutableMap<String, PropertiesFile> = LinkedHashMap()

  override fun canTransformResource(element: FileTreeElement): Boolean {
    if (!patternSpec.isSatisfiedBy(element)) {
      return false
    }
    val propertiesFile =
      propertiesFiles.computeIfAbsent(element.path) {
        PropertiesFile(ignoreDuplicatesFor.patternSpec.isSatisfiedBy(element))
      }
    propertiesFile.sources.add(element.file)
    return true
  }

  override fun hasTransformedResource(): Boolean = true

  override fun transform(context: TransformerContext) {
    val path = context.path

    logger.info("Processing properties for {}", path)

    val props = Properties()
    val propertiesFile = propertiesFiles[path]!!
    try {
      val text =
        context.inputStream.readAllBytes().toString(StandardCharsets.UTF_8).trim('\n', '\r')
      propertiesFile.content.append(text).append("\n")
      props.load(text.reader())
    } catch (e: Exception) {
      throw GradleException("Failed to load properties for $path", e)
    }
    val known = propertiesFile.properties
    props.forEach { (k, v) ->
      val existingValue = known[k]
      if (existingValue != null && existingValue != v) {
        propertiesFile.conflicts
          .computeIfAbsent(k) { LinkedHashSet<Any>().apply { add(existingValue) } }
          .add(v)
      }
      known[k] = v
    }
  }

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    val propFilesWithConflicts =
      propertiesFiles.filter { !it.value.ignoreConflicts && it.value.conflicts.isNotEmpty() }
    if (propFilesWithConflicts.isNotEmpty()) {
      val message =
        "${propFilesWithConflicts.size} properties files to merge have conflicts:" +
          propFilesWithConflicts
            .map {
              "* Properties file ${it.key} merged with conflicting properties. Sources:${
                it.value.sources.joinToString("\n", "\n") { f -> "  * ${f.path}"}
              }\n  Conflicting property values:${
                it.value.conflicts.map { (k, v) -> "    * $k -> $v" }.joinToString("\n", "\n")
              }"
            }
            .joinToString("\n", "\n")
      if (dontFail.get()) {
        logger.error(message)
      } else {
        throw GradleException(message)
      }
    }

    propertiesFiles.forEach { (path, propertiesFile) ->
      logger.info(
        "Adding properties for path {} from {} sources",
        path,
        propertiesFile.sources.size,
      )
      os.putNextEntry(
        ZipEntry(path).apply { time = ShadowCopyAction.CONSTANT_TIME_FOR_ZIP_ENTRIES },
      )
      os.write(propertiesFile.content.toString().toByteArray(StandardCharsets.UTF_8))
      os.closeEntry()
    }
  }

  private companion object {
    private val logger: Logger =
      LoggerFactory.getLogger(DeduplicatingResourceTransformer::class.java)
  }
}
