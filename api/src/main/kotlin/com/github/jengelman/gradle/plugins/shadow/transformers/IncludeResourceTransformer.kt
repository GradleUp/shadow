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
open class IncludeResourceTransformer : Transformer by NoOpTransformer {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    var file: File? = null

    @get:Input
    var resource: String? = null

    override fun hasTransformedResource(): Boolean = file?.exists() == true

    override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
        val entry = ZipEntry(requireNotNull(resource))
        entry.time = getEntryTimestamp(preserveFileTimestamps, entry.time)
        os.putNextEntry(entry)

        requireNotNull(file).inputStream().use { inputStream ->
            inputStream.copyTo(os)
        }
    }
}
