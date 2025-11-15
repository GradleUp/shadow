package com.github.jengelman.gradle.plugins.shadow.internal

import groovy.lang.Closure
import org.gradle.api.GradleException
import org.gradle.api.file.FileTreeElement
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Helper class for the boilerplate overrides, holding a [PatternSet] exposed as a
 * [PatternFilterable].
 */
public open class WithPatternFilterable(
  @get:Internal protected val canBeEmpty: Boolean = false,
  @get:Internal protected val defaultIncludes: Set<String> = setOf(),
) : PatternFilterable {
  @get:Internal internal val patternSet: PatternSet = PatternSet()

  @Input override fun getIncludes(): Set<String> = patternSet.includes

  override fun setIncludes(includes: Iterable<String>): PatternFilterable {
    patternSet.setIncludes(includes)
    return this
  }

  @Input override fun getExcludes(): Set<String> = patternSet.excludes

  override fun setExcludes(excludes: Iterable<String>): PatternFilterable {
    patternSet.setExcludes(excludes)
    return this
  }

  override fun include(vararg includes: String): PatternFilterable {
    patternSet.include(*includes)
    return this
  }

  override fun include(includes: Iterable<String>): PatternFilterable {
    patternSet.include(includes)
    return this
  }

  override fun include(includeSpec: Spec<FileTreeElement>): PatternFilterable {
    patternSet.include(includeSpec)
    return this
  }

  override fun include(includeSpec: Closure<*>): PatternFilterable {
    patternSet.include(includeSpec)
    return this
  }

  override fun exclude(vararg excludes: String): PatternFilterable {
    patternSet.exclude(*excludes)
    return this
  }

  override fun exclude(excludes: Iterable<String>): PatternFilterable {
    patternSet.exclude(excludes)
    return this
  }

  override fun exclude(excludeSpec: Spec<FileTreeElement>): PatternFilterable {
    patternSet.exclude(excludeSpec)
    return this
  }

  override fun exclude(excludeSpec: Closure<*>): PatternFilterable {
    patternSet.exclude(excludeSpec)
    return this
  }

  @get:Internal
  internal val patternSpec: Spec<FileTreeElement> by lazy {
    if (patternSet.isEmpty) {
      if (!defaultIncludes.isEmpty()) {
        patternSet.include(defaultIncludes)
      } else if (!canBeEmpty) {
        throw GradleException("No path patterns specified for 'MergePropertiesResourceTransformer'")
      }
    }
    logger.info(
      "Using patterns spec with includes:{} excludes:{}",
      patternSet.includes,
      patternSet.excludes,
    )
    patternSet.asSpec
  }

  private companion object {
    private val logger: Logger = LoggerFactory.getLogger(WithPatternFilterable::class.java)
  }
}
