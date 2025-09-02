package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocatePathContext
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocatePath

internal class CompositeRelocator(
  private val relocators: Set<Relocator>,
) : Relocator {
  override fun relocatePath(context: RelocatePathContext): String {
    val path = context.path
    for (relocator in relocators) {
      if (relocator.canRelocatePath(path)) {
        return relocator.relocatePath(path)
      }
    }
    return path
  }

  override fun relocateClass(context: RelocateClassContext): String = notImplemented()
  override fun canRelocateClass(className: String): Boolean = notImplemented()
  override fun canRelocatePath(path: String): Boolean = notImplemented()
  override fun applyToSourceContent(sourceContent: String): String = notImplemented()
  private fun notImplemented(): Nothing = error("We don't have to implement this... yet")
}
