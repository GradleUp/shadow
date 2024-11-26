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
    val includedDeps = mutableSetOf<ResolvedDependency>()
    val excludedDeps = mutableSetOf<ResolvedDependency>()
    resolve(configuration.resolvedConfiguration.firstLevelModuleDependencies, includedDeps, excludedDeps)
    return project.files(configuration.files) -
      project.files(excludedDeps.flatMap { it.moduleArtifacts.map(ResolvedArtifact::getFile) })
  }

  override fun resolve(configurations: Collection<Configuration>): FileCollection {
    return configurations.map { resolve(it) }
      .reduceOrNull { acc, fileCollection -> acc + fileCollection }
      ?: project.files()
  }

  /**
   * Exclude dependencies that match the provided spec.
   */
  override fun exclude(spec: Spec<ResolvedDependency>): DependencyFilter = apply {
    excludeSpecs.add(spec)
  }

  /**
   * Include dependencies that match the provided spec.
   */
  override fun include(spec: Spec<ResolvedDependency>): DependencyFilter = apply {
    includeSpecs.add(spec)
  }

  /**
   * Create a spec that matches the provided project notation on group, name, and version.
   */
  override fun project(notation: Map<String, *>): Spec<ResolvedDependency> {
    return dependency(dependency = project.dependencies.project(notation))
  }

  /**
   * Create a spec that matches the default configuration for the provided project path on group, name, and version.
   */
  override fun project(notation: String): Spec<ResolvedDependency> {
    return dependency(
      dependency = project.dependencies.project(
        mapOf(
          "path" to notation,
          "configuration" to "default",
        ),
      ),
    )
  }

  /**
   * Create a spec that matches dependencies using the provided notation on group, name, and version.
   */
  override fun dependency(notation: Any): Spec<ResolvedDependency> {
    return dependency(dependency = project.dependencies.create(notation))
  }

  /**
   * Create a spec that matches the provided dependency on group, name, and version.
   */
  override fun dependency(dependency: Dependency): Spec<ResolvedDependency> {
    return Spec<ResolvedDependency> { resolvedDependency ->
      (dependency.group == null || resolvedDependency.moduleGroup.matches(dependency.group!!.toRegex())) &&
        resolvedDependency.moduleName.matches(dependency.name.toRegex()) &&
        (dependency.version == null || resolvedDependency.moduleVersion.matches(dependency.version!!.toRegex()))
    }
  }

  /**
   * Create a spec that matches the provided closure.
   */
  override fun dependency(closure: Closure<*>): Spec<ResolvedDependency> {
    return Specs.convertClosureToSpec(closure)
  }

  protected fun ResolvedDependency.isIncluded(): Boolean {
    val include = includeSpecs.isEmpty() || includeSpecs.any { it.isSatisfiedBy(this) }
    val exclude = excludeSpecs.isNotEmpty() && excludeSpecs.any { it.isSatisfiedBy(this) }
    return include && !exclude
  }
}
