package com.github.jengelman.gradle.plugins.shadow.internal

import groovy.util.logging.Slf4j
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs

@Slf4j
class DefaultDependencyFilter implements DependencyFilter {

    private final Project project

    private final List<Spec<? super ResolvedDependency>> includeSpecs = []
    private final List<Spec<? super ResolvedDependency>> excludeSpecs = []

    DefaultDependencyFilter(Project project) {
        assert project
        this.project = project
    }

    FileCollection resolve(Configuration configuration) {
        Set<ResolvedDependency> includedDeps = []
        Set<ResolvedDependency> excludedDeps = []
        resolve(configuration.resolvedConfiguration.firstLevelModuleDependencies, includedDeps, excludedDeps)
        return project.files(configuration.files) - project.files(excludedDeps.collect {
            it.moduleArtifacts*.file
        }.flatten())
    }

    FileCollection resolve(Collection<Configuration> configurations) {
        configurations.collect {
            resolve(it)
        }.sum() as FileCollection ?: project.files()
    }

    /**
     * Exclude dependencies that match the provided spec.
     *
     * @param spec
     * @return
     */
    DependencyFilter exclude(Spec<? super ResolvedDependency> spec) {
        excludeSpecs << spec
        return this
    }

    /**
     * Include dependencies that match the provided spec.
     *
     * @param spec
     * @return
     */
    DependencyFilter include(Spec<? super ResolvedDependency> spec) {
        includeSpecs << spec
        return this
    }

    /**
     * Create a spec that matches the provided project notation on group, name, and version
     * @param notation
     * @return
     */
    Spec<? super ResolvedDependency> project(Map<String, ?> notation) {
        dependency(project.dependencies.project(notation))
    }

    /**
     * Create a spec that matches the default configuration for the provided project path on group, name, and version
     *
     * @param notation
     * @return
     */
    Spec<? super ResolvedDependency> project(String notation) {
        dependency(project.dependencies.project(path: notation, configuration: 'default'))
    }

    /**
     * Create a spec that matches dependencies using the provided notation on group, name, and version
     * @param notation
     * @return
     */
    Spec<? super ResolvedDependency> dependency(Object notation) {
        dependency(project.dependencies.create(notation))
    }

    /**
     * Create a spec that matches the provided dependency on group, name, and version
     * @param dependency
     * @return
     */
    Spec<? super ResolvedDependency> dependency(Dependency dependency) {
        this.dependency({ ResolvedDependency it ->
            (!dependency.group || it.moduleGroup.matches(dependency.group)) &&
                    (!dependency.name || it.moduleName.matches(dependency.name)) &&
                    (!dependency.version || it.moduleVersion.matches(dependency.version))
        })
    }

    /**
     * Create a spec that matches the provided closure
     * @param spec
     * @return
     */
    Spec<? super ResolvedDependency> dependency(Closure spec) {
        return Specs.<ResolvedDependency>convertClosureToSpec(spec)
    }


    protected void resolve(Set<ResolvedDependency> dependencies,
                           Set<ResolvedDependency> includedDependencies,
                           Set<ResolvedDependency> excludedDependencies) {
        dependencies.each {
            if (isIncluded(it) ? includedDependencies.add(it) : excludedDependencies.add(it)) {
                resolve(it.children, includedDependencies, excludedDependencies)
            }
        }
    }

    protected boolean isIncluded(ResolvedDependency dependency) {
        boolean include = includeSpecs.empty || includeSpecs.any { it.isSatisfiedBy(dependency) }
        boolean exclude = !excludeSpecs.empty && excludeSpecs.any { it.isSatisfiedBy(dependency) }
        return include && !exclude
    }
}
