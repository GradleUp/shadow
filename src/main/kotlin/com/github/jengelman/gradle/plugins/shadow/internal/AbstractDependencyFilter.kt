package com.github.jengelman.gradle.plugins.shadow.internal

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

  protected val includeSpecs: MutableList<Spec<in ResolvedDependency>> = mutableListOf()
  protected val excludeSpecs: MutableList<Spec<in ResolvedDependency>> = mutableListOf()

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
    return configurations.map { resolve(it) }.reduceOrNull { acc, fileCollection -> acc + fileCollection }
      ?: project.files()
  }

  override fun exclude(spec: Spec<in ResolvedDependency>): DependencyFilter {
    excludeSpecs.add(spec)
    return this
  }

  override fun include(spec: Spec<in ResolvedDependency>): DependencyFilter {
    includeSpecs.add(spec)
    return this
  }

  override fun project(notation: Map<String, *>): Spec<in ResolvedDependency> {
    return dependency(project.dependencies.project(notation))
  }

  override fun project(notation: String): Spec<in ResolvedDependency> {
    return dependency(project.dependencies.project(mapOf("path" to notation, "configuration" to "default")))
  }

  override fun dependency(notation: Any): Spec<in ResolvedDependency> {
    return dependency(project.dependencies.create(notation))
  }

  override fun dependency(dependency: Dependency): Spec<in ResolvedDependency> {
    return dependency { it: ResolvedDependency ->
      (dependency.group == null || it.moduleGroup.matches(dependency.group!!.toRegex())) &&
        (it.moduleName.matches(dependency.name.toRegex())) &&
        (dependency.version == null || it.moduleVersion.matches(dependency.version!!.toRegex()))
    }
  }

  override fun dependency(spec: Closure<*>): Spec<in ResolvedDependency> {
    return Specs.convertClosureToSpec(spec)
  }

  protected fun isIncluded(dependency: ResolvedDependency): Boolean {
    val include = includeSpecs.isEmpty() || includeSpecs.any { it.isSatisfiedBy(dependency) }
    val exclude = excludeSpecs.isNotEmpty() && excludeSpecs.any { it.isSatisfiedBy(dependency) }
    return include && !exclude
  }
}
