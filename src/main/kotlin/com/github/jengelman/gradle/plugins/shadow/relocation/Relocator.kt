package com.github.jengelman.gradle.plugins.shadow.relocation

/**
 * Modified from `org.apache.maven.plugins.shade.relocation.Relocator.java`
 *
 * @author Jason van Zyl
 * @author John Engelman
 */
public interface Relocator {
  public fun canRelocatePath(path: String): Boolean

  public fun relocatePath(context: RelocatePathContext): String

  public fun canRelocateClass(className: String): Boolean

  public fun relocateClass(context: RelocateClassContext): String

  public fun applyToSourceContent(sourceContent: String): String

  public companion object {
    public val ROLE: String = Relocator::class.java.name
  }
}
