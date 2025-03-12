package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.DependencyFilter
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec

internal sealed class AbstractDependencyFilter(
  @Transient private val project: Project,
  @Transient protected val includeSpecs: MutableList<Spec<ResolvedDependency>> = mutableListOf(),
  @Transient protected val excludeSpecs: MutableList<Spec<ResolvedDependency>> = mutableListOf(),
) : DependencyFilter {

  protected abstract fun resolve(
    dependencies: Set<ResolvedDependency>,
    includedDependencies: MutableSet<ResolvedDependency>,
    excludedDependencies: MutableSet<ResolvedDependency>,
  )

  override fun resolve(configuration: Configuration): FileCollection {
    val included = mutableSetOf<ResolvedDependency>()
    val excluded = mutableSetOf<ResolvedDependency>()
    resolve(configuration.resolvedConfiguration.firstLevelModuleDependencies, included, excluded)
    return project.files(configuration.files) -
      project.files(excluded.flatMap { it.moduleArtifacts.map(ResolvedArtifact::getFile) })
  }

  override fun resolve(configurations: Collection<Configuration>): FileCollection {
    return configurations.map { resolve(it) }
      .reduceOrNull { acc, fileCollection -> acc + fileCollection }
      ?: project.files()
  }

  override fun exclude(spec: Spec<ResolvedDependency>) {
    excludeSpecs.add(spec)
  }

  override fun include(spec: Spec<ResolvedDependency>) {
    includeSpecs.add(spec)
  }

  override fun project(notation: Any): Spec<ResolvedDependency> {
    @Suppress("UNCHECKED_CAST")
    val realNotation = when (notation) {
      is ProjectDependency -> return notation.toSpec()
      is Provider<*> -> mapOf("path" to notation.get())
      is String -> mapOf("path" to notation)
      is Map<*, *> -> notation as Map<String, Any>
      else -> throw IllegalArgumentException("Unsupported notation type: ${notation::class.java}")
    }
    return project.dependencies.project(realNotation).toSpec()
  }

  override fun dependency(dependencyNotation: Any): Spec<ResolvedDependency> {
    val realNotation = when (dependencyNotation) {
      is Provider<*> -> dependencyNotation.get()
      else -> dependencyNotation
    }
    return project.dependencies.create(realNotation).toSpec()
  }

  protected fun ResolvedDependency.isIncluded(): Boolean {
    val include = includeSpecs.isEmpty() || includeSpecs.any { it.isSatisfiedBy(this) }
    val exclude = excludeSpecs.isNotEmpty() && excludeSpecs.any { it.isSatisfiedBy(this) }
    return include && !exclude
  }

  private fun Dependency.toSpec(): Spec<ResolvedDependency> {
    return Spec<ResolvedDependency> { resolvedDependency ->
      (group == null || resolvedDependency.moduleGroup.matches(group!!.toRegex())) &&
        resolvedDependency.moduleName.matches(name.toRegex()) &&
        (version == null || resolvedDependency.moduleVersion.matches(version!!.toRegex()))
    }
  }
}
