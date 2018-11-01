package com.github.jengelman.gradle.plugins.shadow.internal

import groovy.util.logging.Slf4j
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency

@Slf4j
class MinimizeDependencyFilter extends AbstractDependencyFilter {
    MinimizeDependencyFilter(Project project) {
        super(project)
    }

    @Override
    protected void resolve(Set<ResolvedDependency> dependencies,
                           Set<ResolvedDependency> includedDependencies,
                           Set<ResolvedDependency> excludedDependencies) {
        dependencies.each { dependency ->
            if (isIncluded(dependency) && includedDependencies.any { it.parents.contains(dependency) } ? includedDependencies.add(dependency) : excludedDependencies.add(dependency)) {
                resolve(dependency.children, includedDependencies, excludedDependencies)
            }
        }
    }
}