package com.github.jengelman.gradle.plugins.shadow.relocation

public data class RelocateClassContext(public val className: String)

public data class RelocatePathContext(public val path: String)

public fun Relocator.relocateClass(className: String): String {
  return relocateClass(RelocateClassContext(className))
}

public fun Relocator.relocatePath(path: String): String {
  return relocatePath(RelocatePathContext(path))
}

/**
 * Relocates all matching class and package names in the given [text].
 *
 * Unlike [relocateClass], which operates on a single class name (subject to prefix/format checks in
 * [Relocator.canRelocateClass] and single-occurrence replacement), this function performs global
 * replacement across arbitrary text content (e.g. `MANIFEST.MF` attributes or ProGuard/R8 rules).
 *
 * For [SimpleRelocator], it directly replaces all occurrences of [SimpleRelocator.pattern] with
 * [SimpleRelocator.shadedPattern]. For generic [Relocator]s, it iteratively calls [relocateClass]
 * until the value converges.
 */
internal fun Relocator.relocateText(text: String): String {
  if (this is SimpleRelocator) {
    return if (rawString || pattern.isEmpty()) {
      text
    } else {
      text.replace(pattern, shadedPattern)
    }
  }
  var newValue = text
  do {
    val value = newValue
    newValue = relocateClass(value)
  } while (value != newValue)
  return newValue
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
 * Unlike [Iterable.relocateClass] which stops at the first matching relocator, this function passes
 * the text through every relocator in a chain-of-responsibility pipeline so that all patterns
 * present in the text are relocated.
 */
internal fun Iterable<Relocator>.relocateText(text: String): String =
  fold(text) { acc, relocator -> relocator.relocateText(acc) }
