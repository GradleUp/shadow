package com.github.jengelman.gradle.plugins.shadow.internal

import org.apache.commons.io.FilenameUtils
import org.apache.log4j.Logger
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.util.PatternSet

class DependencyFilter {
    static Logger log = Logger.getLogger(DependencyFilter.class)

    private final Project project
    private final PatternSet patternSet

    DependencyFilter(Project project) {
        assert project
        this.project = project
        this.patternSet = new PatternSet()
    }

    /**
     * Exclude dependencies that match the provided spec.
     *
     * @param spec
     * @return
     */
    public DependencyFilter exclude(Spec<? super ResolvedDependency> spec) {
        Set<ResolvedDependency> dependencies = findMatchingDependencies(spec,
                project.configurations.runtime.resolvedConfiguration.firstLevelModuleDependencies)
        dependencies.collect { it.moduleArtifacts.file }.flatten().each { File file ->
            this.patternSet.exclude(FilenameUtils.getName(file.path))
        }
        dependencies.each { log.info("Excluding: ${it}")}
        return this
    }

    /**
     * Include dependencies that match the provided spec.
     *
     * @param spec
     * @return
     */
    public DependencyFilter include(Spec<? super ResolvedDependency> spec) {
        Set<ResolvedDependency> dependencies = findMatchingDependencies(spec,
                project.configurations.runtime.resolvedConfiguration.firstLevelModuleDependencies)
        dependencies.collect { it.moduleArtifacts.file }.flatten().each { File file ->
            this.patternSet.include(FilenameUtils.getName(file.path))
        }
        return this
    }

    public PatternSet getPatternSet() {
        return patternSet
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
