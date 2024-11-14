package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency

class DefaultDependencyFilter(
    project: Project,
) : AbstractDependencyFilter(project) {
    override fun resolve(
        dependencies: Set<ResolvedDependency>,
        includedDependencies: MutableSet<ResolvedDependency>,
        excludedDependencies: MutableSet<ResolvedDependency>,
    ) {
        dependencies.forEach {
            if (if (it.isIncluded()) includedDependencies.add(it) else excludedDependencies.add(it)) {
                resolve(it.children, includedDependencies, excludedDependencies)
            }
        }
    }
}
