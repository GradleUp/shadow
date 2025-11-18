package com.github.jengelman.gradle.plugins.shadow.tasks

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.gradle.work.DisableCachingByDefault

/**
 * Helper task to temporarily add to your build script to find resources in the classpath that were
 * identified as duplicates by [com.github.jengelman.gradle.plugins.shadow.transformers.MergePropertiesResourceTransformer] or
 * [com.github.jengelman.gradle.plugins.shadow.transformers.DeduplicatingResourceTransformer].
 *
 * First, add the task to your build script:
 *
 * ```kotlin
 * val findResources by tasks.registering(FindResourceInClasspath::class) {
 *   // add configurations to search for resources in dependency jars
 *   classpath.from(configurations.runtimeClasspath)
 *   // the patterns to search for (it is a Gradle PatternFilterable)
 *   include(
 *     "META-INF/...",
 *   )
 * }
 * ```
 *
 * Then let `shadowJar` depend on the task, or just run the above task manually.
 *
 * ```kotlin
 * tasks.shadowJar {
 *   dependsOn(findResources)
 * }
 * ```
 */
@DisableCachingByDefault(because = "Not worth caching")
public abstract class FindResourceInClasspath(private val patternSet: PatternSet) :
  DefaultTask(),
  PatternFilterable by patternSet {

  @Inject public constructor() : this(PatternSet())

  @get:InputFiles
  @get:Classpath
  public abstract val classpath: ConfigurableFileCollection

  @Input // Trigger task executions after includes changed.
  override fun getIncludes(): MutableSet<String> = patternSet.includes

  @Input // Trigger task executions after excludes changed.
  override fun getExcludes(): MutableSet<String> = patternSet.excludes

  @get:Inject
  protected abstract val archiveOperations: ArchiveOperations

  @TaskAction
  public fun findResources() {
    classpath.forEach { file ->
      logger.lifecycle("scanning {}", file)

      archiveOperations.zipTree(file).matching(patternSet).forEach { entry ->
        logger.lifecycle("  -> {}", entry)
      }
    }
  }
}
