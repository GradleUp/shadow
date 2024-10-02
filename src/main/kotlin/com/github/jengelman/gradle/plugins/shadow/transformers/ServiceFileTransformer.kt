package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet

@CacheableTransformer
public class ServiceFileTransformer private constructor(
    private val patternSet: PatternSet,
) : Transformer,
    PatternFilterable by patternSet {
    private val serviceEntries = mutableMapOf<String, ServiceStream>()

    public constructor() : this(
        PatternSet()
            .include(SERVICES_PATTERN)
            .exclude(GROOVY_EXTENSION_MODULE_DESCRIPTOR_PATTERN),
    )

    public fun setPath(path: String) {
        patternSet.setIncludes(listOf("$path/**"))
    }

    override fun canTransformResource(element: FileTreeElement): Boolean {
        val target = if (element is ShadowCopyAction.ArchiveFileTreeElement) element.asFileTreeElement() else element
        return patternSet.asSpec.isSatisfiedBy(target)
    }

    override fun transform(context: TransformerContext) {
        val lines = context.inputStream.bufferedReader().readLines().toMutableList()
        var targetPath = context.path
        context.relocators.forEach { rel ->
            if (rel.canRelocateClass(File(targetPath).name)) {
                targetPath = rel.relocateClass(RelocateClassContext(targetPath, context.stats))
            }
            lines.forEachIndexed { i, line ->
                if (rel.canRelocateClass(line)) {
                    val lineContext = RelocateClassContext(line, context.stats)
                    lines[i] = rel.relocateClass(lineContext)
                }
            }
        }
        lines.forEach { line ->
            serviceEntries.getOrPut(targetPath) { ServiceStream() }.append(line.toByteArray().inputStream())
        }
    }

    override fun hasTransformedResource(): Boolean = serviceEntries.isNotEmpty()

    override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
        serviceEntries.forEach { (path, stream) ->
            val entry = ZipEntry(path)
            entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
            os.putNextEntry(entry)
            stream.toInputStream().copyTo(os)
            os.closeEntry()
        }
    }

    public class ServiceStream : ByteArrayOutputStream(1024) {
        @Throws(IOException::class)
        public fun append(inputStream: InputStream) {
            if (count > 0 && buf[count - 1] != '\n'.code.toByte() && buf[count - 1] != '\r'.code.toByte()) {
                val newline = "\n".toByteArray()
                write(newline, 0, newline.size)
            }
            inputStream.copyTo(this)
        }

        public fun toInputStream(): InputStream = ByteArrayInputStream(buf, 0, count)
    }

    private companion object {
        const val SERVICES_PATTERN = "META-INF/services/**"
        const val GROOVY_EXTENSION_MODULE_DESCRIPTOR_PATTERN =
            "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule"
    }
}
