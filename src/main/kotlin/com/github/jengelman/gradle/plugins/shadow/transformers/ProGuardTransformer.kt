package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.zipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.util.PatternSet

/**
 * Resources transformer that merges entries in `META-INF/proguard` resources into a single
 * resource. For example, if there are several `META-INF/proguard/app.pro` resources spread across
 * many JARs the individual entries will all be concatenated into a single
 * `META-INF/proguard/app.pro` resource packaged into the resultant JAR produced by the shading
 * process.
 */
@CacheableTransformer
public open class ProGuardTransformer
@JvmOverloads
constructor(patternSet: PatternSet = PatternSet().include(PROGUARD_PATTERN)) :
  PatternFilterableResourceTransformer(patternSet = patternSet) {
  @get:Internal internal val proGuardEntries = mutableMapOf<String, MutableList<String>>()

  @get:Internal // No need to mark this as an input as `getIncludes` is already marked as `@Input`.
  public open var path: String = PROGUARD_PATH
    set(value) {
      field = value
      // Reset the includes to match the new path.
      setIncludes(listOf("$value/**"))
    }

  override fun transform(context: TransformerContext) {
    val lines = proGuardEntries.getOrPut(context.path) { mutableListOf() }
    context.inputStream.bufferedReader().use { it.readLines() }.forEach { line -> lines.add(line) }
  }

  override fun hasTransformedResource(): Boolean = proGuardEntries.isNotEmpty()

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    proGuardEntries.forEach { (path, lines) ->
      os.putNextEntry(zipEntry(path, preserveFileTimestamps))
      os.write(lines.joinToString("\n").toByteArray())
      os.closeEntry()
    }
  }

  private companion object {
    private const val PROGUARD_PATH = "META-INF/proguard"
    private const val PROGUARD_PATTERN = "$PROGUARD_PATH/**"
  }
}
