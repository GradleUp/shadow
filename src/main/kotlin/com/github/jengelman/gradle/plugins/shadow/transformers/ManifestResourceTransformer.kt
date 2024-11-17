package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext.Companion.getEntryTimestamp
import java.io.IOException
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.slf4j.LoggerFactory

/**
 * A resource processor that allows the arbitrary addition of attributes to
 * the first MANIFEST.MF that is found in the set of JARs being processed, or
 * to a newly created manifest for the shaded JAR.
 *
 * Modified from `org.apache.maven.plugins.shade.resource.ManifestResourceTransformer`
 *
 * @author Jason van Zyl
 * @author John Engelman
 */
public open class ManifestResourceTransformer : Transformer {
  private var manifestDiscovered = false
  private var manifest: Manifest? = null

  @get:Optional
  @get:Input
  public var mainClass: String? = null

  @get:Optional
  @get:Input
  public var manifestEntries: MutableMap<String, Attributes>? = null

  override fun canTransformResource(element: FileTreeElement): Boolean {
    val path = element.relativePath.pathString
    return JarFile.MANIFEST_NAME.equals(path, ignoreCase = true)
  }

  override fun transform(context: TransformerContext) {
    // We just want to take the first manifest we come across as that's our project's manifest. This is the behavior
    // now which is situational at best. Right now there is no context passed in with the processing so we cannot
    // tell what artifact is being processed.
    if (!manifestDiscovered) {
      try {
        manifest = Manifest(context.inputStream)
        manifestDiscovered = true
      } catch (e: IOException) {
        logger.warn("Failed to read MANIFEST.MF", e)
      }
    }
  }

  override fun hasTransformedResource(): Boolean = true

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    // If we didn't find a manifest, then let's create one.
    if (manifest == null) {
      manifest = Manifest()
    }

    val attributes = manifest!!.mainAttributes

    mainClass?.let {
      attributes[Attributes.Name.MAIN_CLASS] = it
    }

    manifestEntries?.forEach { (key, value) ->
      attributes[Attributes.Name(key)] = value
    }

    val entry = ZipEntry(JarFile.MANIFEST_NAME)
    entry.time = getEntryTimestamp(preserveFileTimestamps, entry.time)
    os.putNextEntry(entry)
    manifest!!.write(os)
  }

  public open fun attributes(attributes: Map<String, Attributes>): ManifestResourceTransformer = apply {
    if (manifestEntries == null) {
      manifestEntries = LinkedHashMap()
    }
    manifestEntries!!.putAll(attributes)
  }

  private companion object {
    private val logger = LoggerFactory.getLogger(ManifestResourceTransformer::class.java)
  }
}
