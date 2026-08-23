package com.github.jengelman.gradle.plugins.shadow.relocation

public data class RelocateClassContext(public val className: String)

public data class RelocatePathContext(public val path: String)

public fun Relocator.relocateClass(className: String): String {
  return relocateClass(RelocateClassContext(className))
}

public fun Relocator.relocatePath(path: String): String {
  return relocatePath(RelocatePathContext(path))
}

private val IDENTIFIER_PATTERN =
  """(?<![a-zA-Z0-9_$.])([a-zA-Z_$][a-zA-Z0-9_$]*(?:\.[a-zA-Z_$][a-zA-Z0-9_$]*)*)""".toRegex()

/**
 * Relocates all matching class and package names in the given [text].
 *
 * Scans [text] for candidate class or package names, checks [Relocator.canRelocateClass] for each
 * candidate to respect includes, excludes, and pattern boundaries, and replaces matching candidates
 * with [Relocator.relocateClass].
 */
internal fun Relocator.relocateText(text: String): String {
  return IDENTIFIER_PATTERN.replace(text) { matchResult ->
    val candidate = matchResult.value
    if (canRelocateClass(candidate)) {
      relocateClass(candidate)
    } else {
      candidate
    }
  }
}

public fun Iterable<Relocator>.relocateClass(className: String): String {
  forEach { relocator ->
    if (relocator.canRelocateClass(className)) {
      return relocator.relocateClass(className)
    }
  }
  return className
}

public fun Iterable<Relocator>.relocatePath(path: String): String {
  forEach { relocator ->
    if (relocator.canRelocatePath(path)) {
      return relocator.relocatePath(path)
    }
  }
  return path
}

/**
 * Sequentially relocates all matching class and package names in the given [text] across all
 * relocators in this collection.
 *
 * For each candidate class or package name found in [text], delegates to [Iterable.relocateClass]
 * to find the first matching relocator.
 */
internal fun Iterable<Relocator>.relocateText(text: String): String =
  IDENTIFIER_PATTERN.replace(text) { matchResult ->
    relocateClass(matchResult.value)
  }
