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
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet

/**
 * Modified from `org.apache.maven.plugins.shade.resource.ServiceResourceTransformer.java`
 *
 * Resources transformer that appends entries in `META-INF/services` resources into
 * a single resource. For example, if there are several `META-INF/services/org.apache.maven.project.ProjectBuilder`
 * resources spread across many JARs the individual entries will all be concatenated into a single
 * `META-INF/services/org.apache.maven.project.ProjectBuilder` resource packaged into the resultant JAR produced
 * by the shading process.
 *
 * @author jvanzyl
 * @author Charlie Knudsen
 * @author John Engelman
 */
@CacheableTransformer
public class ServiceFileTransformer(
  private val patternSet: PatternSet = PatternSet()
    .include(SERVICES_PATTERN)
    .exclude(GROOVY_EXTENSION_MODULE_DESCRIPTOR_PATTERN),
) : Transformer,
  PatternFilterable by patternSet {
  private val serviceEntries = mutableMapOf<String, ServiceStream>()

  override fun canTransformResource(element: FileTreeElement): Boolean {
    val target = if (element is ShadowCopyAction.ArchiveFileTreeElement) element.asFileTreeElement() else element
    return patternSet.asSpec.isSatisfiedBy(target)
  }

  override fun transform(context: TransformerContext) {
    val lines = requireNotNull(context.inputStream).bufferedReader().readLines().toMutableList()
    var targetPath = context.path
    context.relocators.forEach { rel ->
      if (rel.canRelocateClass(File(targetPath).name)) {
        val classContext = RelocateClassContext.builder().className(targetPath).stats(context.stats).build()
        targetPath = rel.relocateClass(classContext)
      }
      lines.forEachIndexed { i, line ->
        if (rel.canRelocateClass(line)) {
          val lineContext = RelocateClassContext.builder().className(line).stats(context.stats).build()
          lines[i] = rel.relocateClass(lineContext)
        }
      }
    }
    lines.forEach { line ->
      serviceEntries[targetPath] = serviceEntries.getOrDefault(targetPath, ServiceStream()).apply {
        append(ByteArrayInputStream(line.toByteArray()))
      }
    }
  }

  override fun hasTransformedResource(): Boolean = serviceEntries.isNotEmpty()

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    serviceEntries.forEach { (path, stream) ->
      val entry = ZipEntry(path)
      entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
      os.putNextEntry(entry)
      stream.toInputStream().use {
        it.copyTo(os)
      }
      os.closeEntry()
    }
  }

  /**
   * {@inheritDoc}
   */
  @Input
  override fun getIncludes(): Set<String> = patternSet.includes

  /**
   * {@inheritDoc}
   */
  @Input
  override fun getExcludes(): Set<String> = patternSet.excludes

  public fun setPath(path: String): PatternFilterable = apply {
    patternSet.setIncludes(listOf("$path/**"))
  }

  public class ServiceStream : ByteArrayOutputStream(1024) {
    @Throws(IOException::class)
    public fun append(inputStream: InputStream) {
      if (count > 0 && buf[count - 1] != '\n'.code.toByte() && buf[count - 1] != '\r'.code.toByte()) {
        val newline = "\n".toByteArray()
        write(newline, 0, newline.size)
      }
      inputStream.use {
        it.copyTo(this)
      }
    }

    public fun toInputStream(): InputStream = ByteArrayInputStream(buf, 0, count)
  }

  private companion object {
    private const val SERVICES_PATTERN = "META-INF/services/**"
    private const val GROOVY_EXTENSION_MODULE_DESCRIPTOR_PATTERN =
      "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule"
  }
}
