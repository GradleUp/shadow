package com.github.jengelman.gradle.plugins.shadow.transformers

import java.io.ByteArrayOutputStream
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

/**
 * A resource processor that appends content for a resource, separated by a newline.
 *
 * Modified from `org.apache.maven.plugins.shade.resource.AppendingTransformer.java`
 *
 * Modifications
 * @author John Engelman
 */
@CacheableTransformer
open class AppendingTransformer : Transformer {
    /**
     * Defer initialization, see [issue 763](https://github.com/GradleUp/shadow/issues/763).
     */
    private var data: ByteArrayOutputStream? = null

    @get:Optional
    @get:Input
    var resource: String? = null

    override fun canTransformResource(element: FileTreeElement): Boolean {
        return resource.equals(element.relativePath.pathString, ignoreCase = true)
    }

    override fun transform(context: TransformerContext) {
        if (data == null) {
            data = ByteArrayOutputStream()
        }
        context.inputStream.use {
            it.copyTo(data!!)
            data!!.write('\n'.code)
        }
    }

    override fun hasTransformedResource(): Boolean {
        return (data?.size() ?: 0) > 0
    }

    override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
        if (data == null) {
            data = ByteArrayOutputStream()
        }

        val entry = ZipEntry(requireNotNull(resource))
        entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
        os.putNextEntry(entry)

        data!!.toByteArray().inputStream().copyTo(os)
        data!!.reset()
    }
}