package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.zipEntry
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.PATH_LEGACY_GROOVY_EXTENSION_MODULE_DESCRIPTOR
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet

/**
 * Resources transformer that appends entries in `META-INF/services` resources into
 * a single resource. For example, if there are several `META-INF/services/org.apache.maven.project.ProjectBuilder`
 * resources spread across many JARs the individual entries will all be concatenated into a single
 * `META-INF/services/org.apache.maven.project.ProjectBuilder` resource packaged into the resultant JAR produced
 * by the shading process.
 *
 * Modified from [org.apache.maven.plugins.shade.resource.ServicesResourceTransformer.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/resource/ServicesResourceTransformer.java).
 *
 * @author jvanzyl
 * @author Charlie Knudsen
 * @author John Engelman
 */
@CacheableTransformer
public open class ServiceFileTransformer(
  private val patternSet: PatternSet = PatternSet()
    .include(SERVICES_PATTERN)
    .exclude(PATH_LEGACY_GROOVY_EXTENSION_MODULE_DESCRIPTOR),
) : ResourceTransformer,
  PatternFilterable by patternSet {
  @get:Internal
  internal val serviceEntries = mutableMapOf<String, MutableSet<String>>()

  @get:Internal // No need to mark this as an input as `getIncludes` is already marked as `@Input`.
  public open var path: String = SERVICES_PATH
    set(value) {
      field = value
      patternSet.setIncludes(listOf("$value/**"))
    }

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return patternSet.asSpec.isSatisfiedBy(element)
  }

  override fun transform(context: TransformerContext) {
    var resource = context.path.substringAfter("$path/")
    context.relocators.forEach { relocator ->
      if (relocator.canRelocateClass(resource)) {
        val classContext = RelocateClassContext(className = resource)
        resource = relocator.relocateClass(classContext)
        return@forEach
      }
    }
    resource = "$path/$resource"
    val out = serviceEntries.getOrPut(resource) { mutableSetOf() }

    context.inputStream.bufferedReader().use { it.readLines() }.forEach {
      var line = it
      context.relocators.forEach { relocator ->
        if (relocator.canRelocateClass(line)) {
          val lineContext = RelocateClassContext(line)
          line = relocator.relocateClass(lineContext)
        }
      }
      out.add(line)
    }
  }

  override fun hasTransformedResource(): Boolean = serviceEntries.isNotEmpty()

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    serviceEntries.forEach { (path, data) ->
      os.putNextEntry(zipEntry(path, preserveFileTimestamps))
      os.write(data.joinToString("\n").toByteArray())
      os.closeEntry()
    }
  }

  @Input // Trigger task executions after includes changed.
  override fun getIncludes(): MutableSet<String> = patternSet.includes

  @Input // Trigger task executions after excludes changed.
  override fun getExcludes(): MutableSet<String> = patternSet.excludes

  private companion object {
    private const val SERVICES_PATH = "META-INF/services"
    private const val SERVICES_PATTERN = "$SERVICES_PATH/**"
  }
}
