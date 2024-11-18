package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency

internal class MinimizeDependencyFilter(
    project: Project,
) : AbstractDependencyFilter(project) {
    override fun resolve(
        dependencies: Set<ResolvedDependency>,
        includedDependencies: MutableSet<ResolvedDependency>,
        excludedDependencies: MutableSet<ResolvedDependency>,
    ) {
        dependencies.forEach {
            if (it.isIncluded() && !isParentExcluded(excludedDependencies, it)) {
                includedDependencies.add(it)
            } else {
                excludedDependencies.add(it)
            }
            resolve(it.children, includedDependencies, excludedDependencies)
        }
    }

    private fun isParentExcluded(
        excludedDependencies: Set<ResolvedDependency>,
        dependency: ResolvedDependency,
    ): Boolean {
        return excludedDependencies.any { it in dependency.parents }
    }
}
