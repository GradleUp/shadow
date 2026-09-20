package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.DependencyFilter
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.file.FileCollection

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

    val includedDependenciesResults =
      configuration.incoming.resolutionResult.allDependencies
        .filterIsInstance<ResolvedDependencyResult>()
        .filter { dep ->
          includes.any { inc ->
            inc.moduleGroup == dep.selected.moduleVersion?.group &&
              inc.moduleName == dep.selected.moduleVersion?.name &&
              inc.moduleVersion == dep.selected.moduleVersion?.version
          }
        }

    val includedComponentIds = includedDependenciesResults.map { it.selected.id }.toSet()

    return try {
      configuration.incoming
        .artifactView { view ->
          view.withVariantReselection()
          view.attributes { attrs ->
            attrs.attribute(
              Category.CATEGORY_ATTRIBUTE,
              project.objects.named(Category::class.java, Category.DOCUMENTATION),
            )
            attrs.attribute(
              DocsType.DOCS_TYPE_ATTRIBUTE,
              project.objects.named(DocsType::class.java, DocsType.SOURCES),
            )
          }
          view.componentFilter { id -> id in includedComponentIds }
          view.lenient(true)
        }
        .files
    } catch (_: Exception) {
      project.files()
    }
  }
}
