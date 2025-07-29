package com.github.jengelman.gradle.plugins.shadow.relocation

public data class RelocateClassContext(
  public val className: String,
)

public data class RelocatePathContext(
  public val path: String,
)

public fun Relocator.relocateClass(className: String): String {
  return relocateClass(RelocateClassContext(className))
}

public fun Relocator.relocatePath(path: String): String {
  return relocatePath(RelocatePathContext(path))
}
