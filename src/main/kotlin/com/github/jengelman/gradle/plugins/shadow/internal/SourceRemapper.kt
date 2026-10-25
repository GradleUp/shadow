package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocatePathContext
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocatePath

internal fun Iterable<Relocator>.remapSource(sourceContent: String): String {
  var content = sourceContent
  for (relocator in this) {
    content = relocator.applyToSourceContent(content)
  }
  return content
}

/**
 * Remaps the content of the source file at [path], which is relocated to [relocatedPath].
 *
 * If the file is laid out in its package directory, its `package` declaration is set to match
 * [relocatedPath], which is decided per class like class files, as the declaration alone can't
 * match class-level includes and excludes.
 */
internal fun Iterable<Relocator>.remapSourceFile(
  sourceContent: String,
  path: String,
  relocatedPath: String,
): String {
  val content = remapSource(sourceContent)
  val originalPackage = packageRegex.find(sourceContent)?.groupValues?.get(1) ?: return content
  val relocatedPackage = relocatedPath.packageOfPath
  if (originalPackage != path.packageOfPath || relocatedPackage.isEmpty()) return content
  val packageRange = packageRegex.find(content)?.groups?.get(1)?.range ?: return content
  return content.replaceRange(packageRange, relocatedPackage)
}

/**
 * Relocates a source file path by stripping its extension before matching against class/path
 * relocators, ensuring class-level include/exclude patterns work symmetrically with binary classes.
 */
internal fun Iterable<Relocator>.relocateSourcePath(path: String): String {
  if (path.isSourceFile()) {
    val extension = path.substringAfterLast('.', "")
    val pathWithoutExt = path.removeSuffix(".$extension")
    val className = pathWithoutExt.replace('/', '.')

    for (relocator in this) {
      val relocatedWithoutExt =
        when {
          relocator.canRelocatePath(pathWithoutExt) ->
            relocator.relocatePath(RelocatePathContext(pathWithoutExt))
          relocator.canRelocateClass(className) ->
            relocator.relocateClass(RelocateClassContext(className)).replace('.', '/')
          else -> continue
        }
      return "$relocatedWithoutExt.$extension"
    }
    return path
  }

  return relocatePath(path)
}

internal fun String.isSourceFile(): Boolean {
  return endsWith(".java") || endsWith(".kt") || endsWith(".groovy") || endsWith(".scala")
}

private val packageRegex = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)

private val String.packageOfPath: String
  get() = substringBeforeLast('/', "").replace('/', '.')
