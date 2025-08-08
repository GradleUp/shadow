package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.DependencyFilter
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency

internal class DefaultDependencyFilter(
  project: Project,
) : DependencyFilter.AbstractDependencyFilter(project) {
  override fun resolve(
    dependencies: Set<ResolvedDependency>,
    includedDependencies: MutableSet<ResolvedDependency>,
    excludedDependencies: MutableSet<ResolvedDependency>,
  ) {
    dependencies.forEach {
      val added = if (it.isIncluded()) includedDependencies.add(it) else excludedDependencies.add(it)
      if (added) {
        resolve(it.children, includedDependencies, excludedDependencies)
      }
    }
  }
}
