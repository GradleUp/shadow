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
