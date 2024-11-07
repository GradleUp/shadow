package com.github.jengelman.gradle.plugins.shadow.internal

import groovy.util.logging.Slf4j
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency
import org.jetbrains.annotations.NotNull

@Slf4j
class MinimizeDependencyFilter extends AbstractDependencyFilter {

    MinimizeDependencyFilter(Project project) {
        super(project)
    }

    @Override
    protected void resolve(
        @NotNull Set<? extends ResolvedDependency> dependencies,
        @NotNull Set<ResolvedDependency> includedDependencies,
        @NotNull Set<ResolvedDependency> excludedDependencies
    ) {
        dependencies.each {
            if (isIncluded(it) && !isParentExcluded(excludedDependencies, it) ? includedDependencies.add(it) : excludedDependencies.add(it)) {
                resolve(it.children, includedDependencies, excludedDependencies)
            }
        }
    }

    private static boolean isParentExcluded(Set<ResolvedDependency> excludedDependencies, ResolvedDependency dependency) {
        excludedDependencies.any { dependency.parents.contains(it) }
    }
}
