package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext.Companion.getEntryTimestamp
import java.io.File
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

/**
 * A resource processor that allows the addition of an arbitrary file
 * content into the shaded JAR.
 *
 * Modified from `org.apache.maven.plugins.shade.resource.IncludeResourceTransformer.java`
 *
 * @author John Engelman
 */
public open class IncludeResourceTransformer : Transformer by NoOpTransformer {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  public var file: File? = null

  @get:Input
  public var resource: String? = null

  override fun hasTransformedResource(): Boolean = file?.exists() == true

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    check(file != null) { "file must be set" }
    check(resource != null) { "resource must be set" }

    val entry = ZipEntry(resource)
    entry.time = getEntryTimestamp(preserveFileTimestamps, entry.time)
    os.putNextEntry(entry)

    file!!.inputStream().use { inputStream ->
      inputStream.copyTo(os)
    }
  }
}
