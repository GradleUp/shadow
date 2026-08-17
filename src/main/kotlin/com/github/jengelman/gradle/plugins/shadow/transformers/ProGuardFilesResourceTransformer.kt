package com.github.jengelman.gradle.plugins.shadow.transformers

import com.github.jengelman.gradle.plugins.shadow.internal.checkDupStrategy
import com.github.jengelman.gradle.plugins.shadow.internal.writeEntry
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocateClass
import com.github.jengelman.gradle.plugins.shadow.relocation.relocatePath
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.util.PatternSet

/**
 * Resources transformer that merges R8/ProGuard rule files under `META-INF/proguard/`.
 *
 * Duplicate files with the same name under the target path will be merged into a single file in the
 * output JAR, while distinct file names remain separate. Class names and package patterns within
 * the rules are relocated according to the configured relocators.
 */
@CacheableTransformer
public open class ProGuardFilesResourceTransformer
@JvmOverloads
constructor(patternSet: PatternSet = PatternSet().include("META-INF/proguard/**")) :
  PatternFilterableResourceTransformer(patternSet = patternSet) {
  @get:Internal internal val proGuardEntries = mutableMapOf<String, MutableList<String>>()

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return super.canTransformResource(element).also { flag -> checkDupStrategy(flag, element) }
  }

  override fun transform(context: TransformerContext) {
    with(context) {
      val lines =
        inputStream.bufferedReader().readLines().map { line ->
          relocators.relocateLine(line)
        }
      val targetPath = relocators.relocatePath(path)
      proGuardEntries.getOrPut(targetPath) { mutableListOf() }.addAll(lines)
    }
  }

  override fun hasTransformedResource(): Boolean = proGuardEntries.isNotEmpty()

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    proGuardEntries.forEach { (path, lines) ->
      os.writeEntry(path, preserveFileTimestamps) {
        write(lines.joinToString("\n").toByteArray())
      }
    }
  }

  private companion object {
    /**
     * Matches Java class names, fully qualified class names, package wildcards (e.g. `com.foo.**`),
     * and inner classes (`com.foo.Bar$Inner`).
     */
    val CLASS_PATTERN =
      """(?<![a-zA-Z0-9_$.])([a-zA-Z_$][a-zA-Z0-9_$]*(?:\.[a-zA-Z0-9_$*?]+)+)""".toRegex()

    fun Iterable<Relocator>.relocateLine(line: String): String {
      return when {
        line.isBlank() || line.trimStart().startsWith("#") -> line
        else ->
          CLASS_PATTERN.replace(line) { matchResult ->
            relocateClass(matchResult.value)
          }
      }
    }
  }
}
