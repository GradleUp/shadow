package com.github.jengelman.gradle.plugins.shadow.transformers

import org.gradle.api.file.FileTreeElement
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet

/**
 * A base class for resource transformers that support pattern filtering.
 *
 * @param patternSet The [PatternSet] used for filtering resources.
 */
public abstract class PatternFilterableResourceTransformer(
  @Internal public val patternSet: PatternSet,
) : ResourceTransformer by ResourceTransformer.Companion,
  PatternFilterable by patternSet {

  @get:Internal
  protected val patternSpec: Spec<FileTreeElement> by lazy(LazyThreadSafetyMode.NONE) {
    // Cache the spec to prevent some unnecessary allocations during runtime.
    patternSet.asSpec
  }

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return patternSpec.isSatisfiedBy(element)
  }

  @Input // Trigger task executions after includes changed.
  override fun getIncludes(): MutableSet<String> = patternSet.includes

  @Input // Trigger task executions after excludes changed.
  override fun getExcludes(): MutableSet<String> = patternSet.excludes
}
