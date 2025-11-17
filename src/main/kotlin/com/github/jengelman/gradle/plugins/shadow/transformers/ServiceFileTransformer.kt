package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.zipEntry
import com.github.jengelman.gradle.plugins.shadow.relocation.relocateClass
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer.Companion.PATH_LEGACY_GROOVY_EXTENSION_MODULE_DESCRIPTOR
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.tasks.Internal
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
public open class ServiceFileTransformer @JvmOverloads constructor(
  patternSet: PatternSet = PatternSet()
    .include(SERVICES_PATTERN)
    .exclude(PATH_LEGACY_GROOVY_EXTENSION_MODULE_DESCRIPTOR),
) : PatternFilterableResourceTransformer(patternSet = patternSet) {
  @get:Internal
  internal val serviceEntries = mutableMapOf<String, MutableSet<String>>()

  @Deprecated("Use `setIncludes` instead.", ReplaceWith("setIncludes()"))
  @get:Internal // No need to mark this as an input as `getIncludes` is already marked as `@Input`.
  public open var path: String = SERVICES_PATH
    set(value) {
      field = value
      patternSet.setIncludes(listOf("$value/**"))
    }

  override fun transform(context: TransformerContext) {
    val resource = path + "/" +
      context.relocators.relocateClass(context.path.substringAfter("$path/"))
    val out = serviceEntries.getOrPut(resource) { mutableSetOf() }
    context.inputStream.bufferedReader().use { it.readLines() }.forEach { line ->
      out.add(context.relocators.relocateClass(line))
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

  private companion object {
    private const val SERVICES_PATH = "META-INF/services"
    private const val SERVICES_PATTERN = "$SERVICES_PATH/**"
  }
}
