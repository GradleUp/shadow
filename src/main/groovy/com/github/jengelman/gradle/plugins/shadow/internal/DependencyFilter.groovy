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
class DependencyFilter {

    private final Project project

    private final List<Spec<? super ResolvedDependency>> includeSpecs = []
    private final List<Spec<? super ResolvedDependency>> excludeSpecs = []

    DependencyFilter(Project project) {
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

    void resolve(Set<ResolvedDependency> dependencies,
                 Set<ResolvedDependency> includedDependencies,
                 Set<ResolvedDependency> excludedDependencies) {
        dependencies.each {
            println "Processing ${it.name}"
            if (isIncluded(it)) {
                if (includedDependencies.add(it)) {
                    resolve(it.children, includedDependencies, excludedDependencies)
                }
            } else {
                if (excludedDependencies.add(it)) {
                    resolve(it.children, includedDependencies, excludedDependencies)
                }
            }
        }
    }

    private boolean isIncluded(ResolvedDependency dependency) {
        boolean include = includeSpecs.empty || includeSpecs.any { it.isSatisfiedBy(dependency) }
        boolean exclude = !excludeSpecs.empty && excludeSpecs.any { it.isSatisfiedBy(dependency) }
        return include && !exclude
    }

    /**
     * Exclude dependencies that match the provided spec.
     *
     * @param spec
     * @return
     */
    public DependencyFilter exclude(Spec<? super ResolvedDependency> spec) {
        excludeSpecs << spec
        return this
    }

    /**
     * Include dependencies that match the provided spec.
     *
     * @param spec
     * @return
     */
    public DependencyFilter include(Spec<? super ResolvedDependency> spec) {
        includeSpecs << spec
        return this
    }

    /**
     * Create a spec that matches the provided project notation on group, name, and version
     * @param notation
     * @return
     */
    public Spec<? super ResolvedDependency> project(Map<String, ?> notation) {
        dependency(project.dependencies.project(notation))
    }

    /**
     * Create a spec that matches the default configuration for the provided project path on group, name, and version
     *
     * @param notation
     * @return
     */
    public Spec<? super ResolvedDependency> project(String notation) {
        dependency(project.dependencies.project(path: notation, configuration: 'default'))
    }

    /**
     * Create a spec that matches dependencies using the provided notation on group, name, and version
     * @param notation
     * @return
     */
    public Spec<? super ResolvedDependency> dependency(Object notation) {
        dependency(project.dependencies.create(notation))
    }

    /**
     * Create a spec that matches the provided dependency on group, name, and version
     * @param dependency
     * @return
     */
    public Spec<? super ResolvedDependency> dependency(Dependency dependency) {
        this.dependency({ ResolvedDependency it ->
            (!dependency.group || dependency.group == it.moduleGroup) &&
                    (!dependency.name || dependency.name == it.moduleName) &&
                    (!dependency.version || dependency.version == it.moduleVersion)
        })
    }

    /**
     * Create a spec that matches the provided closure
     * @param spec
     * @return
     */
    public Spec<? super ResolvedDependency> dependency(Closure spec) {
        return Specs.<ResolvedDependency>convertClosureToSpec(spec)
    }

    /**
     * Support method for querying the resolved dependency graph using maven/project coordinates
     * @param spec
     * @param dependencies
     * @return
     */
    protected Set<ResolvedDependency> findMatchingDependencies(Closure spec,
                                                               Set<ResolvedDependency> dependencies) {
        findMatchingDependencies(
                Specs.<? super ResolvedDependency>convertClosureToSpec(spec), dependencies)
    }


    /**
     * Support method for querying the resolved dependency graph using maven/project coordinates
     * @param spec
     * @param dependencies
     * @return
     */
    protected Set<ResolvedDependency> findMatchingDependencies(Spec<? super ResolvedDependency> spec,
                                                                 Set<ResolvedDependency> dependencies) {
        Set<ResolvedDependency> visitedDependencies = new HashSet<ResolvedDependency>()
        return findMatchingDependenciesImpl(visitedDependencies, spec, dependencies)
    }

    /**
     * Impl method for querying the resolved dependency graph using maven/project coordinates
     * @param spec
     * @param dependencies
     * @return
     */
    private Set<ResolvedDependency> findMatchingDependenciesImpl(Set<ResolvedDependency> visitedDependencies,
                                                                 Spec<? super ResolvedDependency> spec,
                                                                 Set<ResolvedDependency> dependencies) {

        Set<ResolvedDependency> matched = []
        dependencies.each {
            if (!visitedDependencies.contains(it)) {
                visitedDependencies.add(it)
                if (spec.isSatisfiedBy(it)) {
                    matched.add(it)
                }
                matched.addAll(findMatchingDependenciesImpl(visitedDependencies, spec, it.children))
            }
        }
        return matched
    }
}
