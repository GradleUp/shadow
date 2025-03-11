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
    return when (notation) {
      is ProjectDependency -> dependency(notation)
      is Provider<*> -> project(notation.get() as String)
      is String -> project(notation)
      is Map<*, *> -> project(notation as Map<String, *>)
      else -> error("Unsupported notation type: ${notation::class.java}")
    }
  }

  override fun project(notation: Map<String, *>): Spec<ResolvedDependency> {
    return dependency(project.dependencies.project(notation))
  }

  override fun project(path: String): Spec<ResolvedDependency> {
    return project(mapOf("path" to path))
  }

  override fun dependency(dependencyNotation: Any): Spec<ResolvedDependency> {
    val realNotation = when (dependencyNotation) {
      is Provider<*> -> dependencyNotation.get()
      else -> dependencyNotation
    }
    return dependency(project.dependencies.create(realNotation))
  }

  override fun dependency(dependency: Dependency): Spec<ResolvedDependency> {
    return Spec<ResolvedDependency> { resolvedDependency ->
      (dependency.group == null || resolvedDependency.moduleGroup.matches(dependency.group!!.toRegex())) &&
        resolvedDependency.moduleName.matches(dependency.name.toRegex()) &&
        (dependency.version == null || resolvedDependency.moduleVersion.matches(dependency.version!!.toRegex()))
    }
  }

  protected fun ResolvedDependency.isIncluded(): Boolean {
    val include = includeSpecs.isEmpty() || includeSpecs.any { it.isSatisfiedBy(this) }
    val exclude = excludeSpecs.isNotEmpty() && excludeSpecs.any { it.isSatisfiedBy(this) }
    return include && !exclude
  }
}
