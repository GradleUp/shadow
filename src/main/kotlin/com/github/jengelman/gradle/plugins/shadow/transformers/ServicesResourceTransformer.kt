package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopyAction
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.PATH_LEGACY_GROOVY_EXTENSION_MODULE_DESCRIPTOR
import java.nio.charset.StandardCharsets
import java.util.Scanner
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet

@CacheableTransformer
public open class ServicesResourceTransformer(
  private val patternSet: PatternSet = PatternSet()
    .include(SERVICES_PATTERN)
    .exclude(PATH_LEGACY_GROOVY_EXTENSION_MODULE_DESCRIPTOR),
) : Transformer,
  PatternFilterable by patternSet {

  @get:Internal
  internal val serviceEntries = mutableMapOf<String, MutableSet<String>>()

  override fun canTransformResource(element: FileTreeElement): Boolean {
    val target = if (element is ShadowCopyAction.ArchiveFileTreeElement) element.asFileTreeElement() else element
    return patternSet.asSpec.isSatisfiedBy(target)
  }

  override fun hasTransformedResource(): Boolean = serviceEntries.isNotEmpty()

  override fun transform(context: TransformerContext) {
    var resource = context.path.substring(SERVICES_PATH.length + 1)
    context.relocators.forEach { relocator ->
      if (relocator.canRelocateClass(resource)) {
        val classContext = RelocateClassContext.builder().className(resource).stats(context.stats).build()
        resource = relocator.relocateClass(classContext)
        return@forEach
      }
    }
    resource = "$SERVICES_PATH/$resource"

    val out = serviceEntries.computeIfAbsent(resource) { mutableSetOf() }

    Scanner(context.inputStream, StandardCharsets.UTF_8.name()).use { scanner ->
      while (scanner.hasNextLine()) {
        var line = scanner.nextLine()
        context.relocators.forEach { relocator ->
          if (relocator.canRelocateClass(line)) {
            val lineContext = RelocateClassContext.builder().className(line).stats(context.stats).build()
            line = relocator.relocateClass(lineContext)
          }
        }
        out.add(line)
      }
    }
  }

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    serviceEntries.forEach { (path, data) ->
      val entry = ZipEntry(path)
      entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
      os.putNextEntry(entry)
      data.forEach { line ->
        os.write(line.toByteArray(StandardCharsets.UTF_8))
        os.write("\n".toByteArray(StandardCharsets.UTF_8))
      }
      os.closeEntry()
    }
  }

  public open fun setPath(path: String): PatternFilterable = apply {
    patternSet.setIncludes(listOf("$path/**"))
  }

  @Input // TODO: https://github.com/GradleUp/shadow/issues/1202
  override fun getIncludes(): MutableSet<String> = patternSet.includes

  @Input // TODO: https://github.com/GradleUp/shadow/issues/1202
  override fun getExcludes(): MutableSet<String> = patternSet.excludes

  private companion object {
    private const val SERVICES_PATH = "META-INF/services"
    private const val SERVICES_PATTERN = "$SERVICES_PATH/**"
  }
}
