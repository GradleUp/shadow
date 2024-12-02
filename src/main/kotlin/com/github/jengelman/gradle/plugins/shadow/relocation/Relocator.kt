package com.github.jengelman.gradle.plugins.shadow.relocation

public typealias MavenRelocator = org.apache.maven.plugins.shade.relocation.Relocator

/**
 * Modified from [org.apache.maven.plugins.shade.relocation.Relocator.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/relocation/Relocator.java).
 *
 * @author Jason van Zyl
 * @author John Engelman
 */
@JvmDefaultWithCompatibility
public interface Relocator : MavenRelocator {
  public override fun canRelocatePath(path: String): Boolean

  public override fun relocatePath(clazz: String): String = relocatePath(RelocatePathContext(clazz))

  public override fun canRelocateClass(className: String): Boolean

  public override fun relocateClass(clazz: String): String = relocateClass(RelocateClassContext(clazz))

  public override fun applyToSourceContent(sourceContent: String): String

  public fun relocatePath(context: RelocatePathContext): String

  public fun relocateClass(context: RelocateClassContext): String

  public companion object {
    public val ROLE: String = Relocator::class.java.name
  }
}
