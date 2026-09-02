package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.DependencyFilter
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.FileCollection
import org.gradle.jvm.JvmLibrary
import org.gradle.language.base.artifact.SourcesArtifact

internal class DefaultDependencyFilter(@Transient private val project: Project) :
  DependencyFilter.AbstractDependencyFilter(project) {
  override fun resolve(
    dependencies: Set<ResolvedDependency>,
    includedDependencies: MutableSet<ResolvedDependency>,
    excludedDependencies: MutableSet<ResolvedDependency>,
  ) {
    dependencies.forEach { dep ->
      val added =
        if (dep.isIncluded()) includedDependencies.add(dep) else excludedDependencies.add(dep)
      if (added) {
        resolve(dep.children, includedDependencies, excludedDependencies)
      }
    }
  }

  fun resolveSourcesJars(configurations: Collection<Configuration>): FileCollection {
    return configurations
      .map { resolveSourcesJars(it) }
      .reduceOrNull { acc, fileCollection -> acc + fileCollection } ?: project.files()
  }

  private fun resolveSourcesJars(configuration: Configuration): FileCollection {
    val includes = mutableSetOf<ResolvedDependency>()
    val excludes = mutableSetOf<ResolvedDependency>()
    resolve(
      dependencies = configuration.resolvedConfiguration.firstLevelModuleDependencies,
      includedDependencies = includes,
      excludedDependencies = excludes,
    )
    val componentIds =
      configuration.incoming.resolutionResult.allDependencies
        .filterIsInstance<ResolvedDependencyResult>()
        .map { it.selected.id }
        .filterIsInstance<ModuleComponentIdentifier>()
        .filter { id ->
          includes.any {
            it.moduleGroup == id.group &&
              it.moduleName == id.module &&
              it.moduleVersion == id.version
          }
        }
        .toSet()
    val files =
      project.dependencies
        .createArtifactResolutionQuery()
        .forComponents(componentIds)
        .withArtifacts(JvmLibrary::class.java, SourcesArtifact::class.java)
        .execute()
        .resolvedComponents
        .flatMap { it.getArtifacts(SourcesArtifact::class.java) }
        .filterIsInstance<ResolvedArtifactResult>()
        .map { it.file }
    return project.files(files)
  }
}
