package com.github.jengelman.gradle.plugins.shadow.transformers

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.jar.JarFile.MANIFEST_NAME
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
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
open class ManifestAppenderTransformer : Transformer {
    private var manifestContents = ByteArray(0)
    private val _attributes = mutableListOf<Pair<String, Comparable<*>>>()

    @get:Input
    open val attributes: List<Pair<String, Comparable<*>>> get() = _attributes

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

    override fun hasTransformedResource(): Boolean = _attributes.isNotEmpty()

    override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
        val entry = ZipEntry(MANIFEST_NAME)
        entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
        os.putNextEntry(entry)
        os.write(manifestContents)

        if (_attributes.isNotEmpty()) {
            for ((key, value) in _attributes) {
                os.write(key.toByteArray())
                os.write(SEPARATOR)
                os.write(value.toString().toByteArray())
                os.write(EOL)
            }
            os.write(EOL)
            _attributes.clear()
        }
    }

    open fun append(name: String, value: Comparable<*>): ManifestAppenderTransformer = apply {
        _attributes.add(Pair(name, value))
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(ManifestAppenderTransformer::class.java)
        private val EOL = "\r\n".toByteArray()
        private val SEPARATOR = ": ".toByteArray()
    }
}
