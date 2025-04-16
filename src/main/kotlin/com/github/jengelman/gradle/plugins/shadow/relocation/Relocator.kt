package com.github.jengelman.gradle.plugins.shadow.relocation

import com.github.jengelman.gradle.plugins.shadow.transformers.CacheableTransformer
import org.gradle.api.tasks.Internal

/**
 * Modified from [org.apache.maven.plugins.shade.relocation.Relocator.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/relocation/Relocator.java).
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

  @get:Internal
  public val isSkipStringLiteral: Boolean

  public companion object {
    public val ROLE: String = Relocator::class.java.name
  }
}

/**
 * Marks that a given instance of [Relocator] is compatible with the Gradle build cache.
 * In other words, it has its appropriate inputs annotated so that Gradle can consider them when
 * determining the cache key.
 *
 * @see [CacheableTransformer]
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
public annotation class CacheableRelocator
