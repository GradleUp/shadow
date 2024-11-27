package com.github.jengelman.gradle.plugins.shadow.transformers

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.jar.JarFile.MANIFEST_NAME
import javax.inject.Inject
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.slf4j.LoggerFactory

/**
 * A resource processor that can append arbitrary attributes to the first MANIFEST.MF
 * that is found in the set of JARs being processed. The attributes are appended in
 * the specified order, and duplicates are allowed.
 *
 * Modified from [ManifestResourceTransformer].
 * @author Chris Rankin
 */
public open class ManifestAppenderTransformer @Inject constructor(
  final override val objectFactory: ObjectFactory,
) : Transformer {
  private var manifestContents = ByteArray(0)

  @Suppress("UNCHECKED_CAST")
  @get:Input
  public open val attributes: ListProperty<Pair<String, Comparable<*>>> =
    objectFactory.listProperty(Pair::class.java) as ListProperty<Pair<String, Comparable<*>>>

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return MANIFEST_NAME.equals(element.relativePath.pathString, ignoreCase = true)
  }

  override fun transform(context: TransformerContext) {
    if (manifestContents.isEmpty()) {
      try {
        context.inputStream.use { inputStream ->
          val outputStream = ByteArrayOutputStream()
          inputStream.copyTo(outputStream)
          manifestContents = outputStream.toByteArray()
        }
      } catch (e: IOException) {
        logger.warn("Failed to read MANIFEST.MF", e)
      }
    }
  }

  override fun hasTransformedResource(): Boolean = attributes.get().isNotEmpty()

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    val entry = ZipEntry(MANIFEST_NAME)
    entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
    os.putNextEntry(entry)
    os.write(manifestContents)

    if (attributes.get().isNotEmpty()) {
      for ((key, value) in attributes.get()) {
        os.write(key.toByteArray())
        os.write(SEPARATOR)
        os.write(value.toString().toByteArray())
        os.write(EOL)
      }
      os.write(EOL)
      attributes.empty()
    }
  }

  public open fun append(name: String, value: Comparable<*>): ManifestAppenderTransformer = apply {
    attributes.add(Pair(name, value))
  }

  private companion object {
    private val logger = LoggerFactory.getLogger(ManifestAppenderTransformer::class.java)
    private val EOL = "\r\n".toByteArray()
    private val SEPARATOR = ": ".toByteArray()
  }
}
