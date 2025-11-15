package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.internal.WithPatternFilterable
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.util.PatternFilterable

/**
 * Helper task to temporarily add to your build script to find resources in the classpath that were
 * identified as duplicates by [com.github.jengelman.gradle.plugins.shadow.transformers.MergePropertiesResourceTransformer] or
 * [com.github.jengelman.gradle.plugins.shadow.transformers.DeduplicatingResourceTransformer].
 *
 * First, add the task to your build script:
 * ```kotlin
 * val findResources by tasks.registering(FindResourceInClasspath::class) {
 *   // add configurations to search for resources in dependency jars
 *   classpath.from(configurations.runtimeClasspath)
 *   // the patterns to search for (it is a Gradle PatternFilterable)
 *   resources.include(
 *     "META-INF/...",
 *   )
 * }
 * ```
 *
 * Then let `shadowJar` depend on the task, or just run the above task manually.
 *
 * ```kotlin
 * tasks.named("shadowJar") {
 *   dependsOn(findResources)
 * }
 * ```
 */
@Suppress("unused")
@CacheableTask
public abstract class FindResourceInClasspath : DefaultTask() {
  private val patterns = WithPatternFilterable()

  @get:Nested public val resources: PatternFilterable = patterns

  @get:InputFiles @get:Classpath
  public abstract val classpath: ConfigurableFileCollection

  @TaskAction
  internal fun findResources() {
    classpath.forEach { file ->
      logger.lifecycle("scanning {}", file)

      project.zipTree(file).matching(patterns.patternSet).forEach { entry ->
        logger.lifecycle("  -> {}", entry)
      }
    }
  }
}
