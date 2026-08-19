package com.github.jengelman.gradle.plugins.shadow.relocation

public data class RelocateClassContext(public val className: String)

public data class RelocatePathContext(public val path: String)

public fun Relocator.relocateClass(className: String): String {
  return relocateClass(RelocateClassContext(className))
}

public fun Relocator.relocatePath(path: String): String {
  return relocatePath(RelocatePathContext(path))
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
