package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.file.FileCollection
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
    val result = project.files(configuration.files) -
      project.files(excluded.flatMap { it.moduleArtifacts.map(ResolvedArtifact::getFile) })
    return result.files.map { project.zipTree(it) }
      .reduceOrNull { acc, fileTreeElement -> acc + fileTreeElement }
      ?: project.files()
  }

  override fun resolve(configurations: Collection<Configuration>): FileCollection {
    return configurations.map { resolve(it) }
      .reduceOrNull { acc, fileCollection -> acc + fileCollection }
      ?: project.files()
  }

  override fun exclude(spec: Spec<ResolvedDependency>): DependencyFilter = apply {
    excludeSpecs.add(spec)
  }

  override fun include(spec: Spec<ResolvedDependency>): DependencyFilter = apply {
    includeSpecs.add(spec)
  }

  override fun project(notation: Map<String, *>): Spec<ResolvedDependency> {
    return dependency(project.dependencies.project(notation))
  }

  override fun project(path: String): Spec<ResolvedDependency> {
    return project(mapOf("path" to path))
  }

  override fun dependency(dependencyNotation: Any): Spec<ResolvedDependency> {
    return dependency(project.dependencies.create(dependencyNotation))
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
