package com.github.jengelman.gradle.plugins.shadow.transformers

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
 * Modified from org.apache.maven.plugins.shade.resource.IncludeResourceTransformer.java
 *
 * @author John Engelman
 */
public class IncludeResourceTransformer : Transformer by NoOpTransformer {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public lateinit var file: File

    @Input
    public lateinit var resource: String

    override fun hasTransformedResource(): Boolean {
        return this::file.isInitialized && file.exists()
    }

    override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
        val entry = ZipEntry(resource)
        entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
        os.putNextEntry(entry)

        file.inputStream().copyTo(os)
    }
}
