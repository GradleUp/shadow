package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.DependencyFilter
import groovy.lang.Closure
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs

internal abstract class AbstractDependencyFilter(protected val project: Project) : DependencyFilter {
  private val includeSpecs = mutableListOf<Spec<ResolvedDependency>>()
  private val excludeSpecs = mutableListOf<Spec<ResolvedDependency>>()

  protected abstract fun resolve(
    dependencies: Set<ResolvedDependency>,
    includedDependencies: MutableSet<ResolvedDependency>,
    excludedDependencies: MutableSet<ResolvedDependency>,
  )

  override fun resolve(configuration: FileCollection): FileCollection {
    val includedDeps = mutableSetOf<ResolvedDependency>()
    val excludedDeps = mutableSetOf<ResolvedDependency>()
    resolve(
      (configuration as Configuration).resolvedConfiguration.firstLevelModuleDependencies,
      includedDeps,
      excludedDeps,
    )
    return project.files(configuration.files) -
      project.files(excludedDeps.flatMap { it.moduleArtifacts.map(ResolvedArtifact::getFile) })
  }

  override fun resolve(configurations: Collection<FileCollection>): FileCollection {
    return configurations.map { resolve(it) }
      .reduceOrNull { acc, fileCollection -> acc + fileCollection } ?: project.files()
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

  override fun project(notation: String): Spec<ResolvedDependency> {
    return dependency(
      project.dependencies.project(
        mapOf(
          "path" to notation,
          "configuration" to "default",
        ),
      ),
    )
  }

  override fun dependency(notation: Any): Spec<ResolvedDependency> {
    return dependency(project.dependencies.create(notation))
  }

  override fun dependency(dependency: Dependency): Spec<ResolvedDependency> {
    return dependency { it: ResolvedDependency ->
      (dependency.group == null || it.moduleGroup.matches(dependency.group!!.toRegex())) &&
        (it.moduleName.matches(dependency.name.toRegex())) &&
        (dependency.version == null || it.moduleVersion.matches(dependency.version!!.toRegex()))
    }
  }

  override fun dependency(spec: Closure<*>): Spec<ResolvedDependency> {
    return Specs.convertClosureToSpec(spec)
  }

  protected fun ResolvedDependency.isIncluded(): Boolean {
    val include = includeSpecs.isEmpty() || includeSpecs.any { it.isSatisfiedBy(this) }
    val exclude = excludeSpecs.isNotEmpty() && excludeSpecs.any { it.isSatisfiedBy(this) }
    return include && !exclude
  }
}
