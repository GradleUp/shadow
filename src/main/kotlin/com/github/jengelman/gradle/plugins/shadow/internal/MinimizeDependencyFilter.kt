package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.DependencyFilter
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency

internal class MinimizeDependencyFilter(
  project: Project,
) : DependencyFilter.AbstractDependencyFilter(project) {
  override fun resolve(
    dependencies: Set<ResolvedDependency>,
    includedDependencies: MutableSet<ResolvedDependency>,
    excludedDependencies: MutableSet<ResolvedDependency>,
  ) {
    dependencies.forEach { dep ->
      val added = if (dep.isIncluded() && !excludedDependencies.any(dep.parents::contains)) {
        includedDependencies.add(dep)
      } else {
        excludedDependencies.add(dep)
      }
      if (added) {
        resolve(dep.children, includedDependencies, excludedDependencies)
      }
    }
  }
}
