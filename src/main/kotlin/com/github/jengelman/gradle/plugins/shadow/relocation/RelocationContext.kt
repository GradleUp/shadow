package com.github.jengelman.gradle.plugins.shadow.relocation

@JvmInline
public value class RelocateClassContext(
  public val className: String,
)

@JvmInline
public value class RelocatePathContext(
  public val path: String,
)

public fun Relocator.relocateClass(className: String): String {
  return relocateClass(RelocateClassContext(className))
}

public fun Relocator.relocatePath(path: String): String {
  return relocatePath(RelocatePathContext(path))
}
