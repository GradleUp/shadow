package com.github.jengelman.gradle.plugins.shadow.transformers

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet

public abstract class PatternFilterableResourceTransformer(
  @Internal public val patternSet: PatternSet,
) : ResourceTransformer,
  PatternFilterable by patternSet {

  @Input // Trigger task executions after includes changed.
  override fun getIncludes(): MutableSet<String> = patternSet.includes

  @Input // Trigger task executions after excludes changed.
  override fun getExcludes(): MutableSet<String> = patternSet.excludes
}
