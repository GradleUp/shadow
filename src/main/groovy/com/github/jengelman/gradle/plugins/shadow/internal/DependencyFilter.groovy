package com.github.jengelman.gradle.plugins.shadow.internal

import org.apache.commons.io.FilenameUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.util.PatternSet

class DependencyFilter {

    private final Project project
    private final CopySpecInternal mainSpec

    DependencyFilter(Project project) {
        assert project
        this.project = project
        this.mainSpec = (CopySpecInternal) project.copySpec {}
    }

    public PatternSet getPatternSet() {
        mainSpec.getPatternSet()
    }

    /**
     * Exclude dependencies that match the provided spec.
     *
     * @param spec
     * @param includeTransitive exclude the transitive dependencies of any dependency that matches the spec.
     * @return
     */
    public DependencyFilter exclude(Spec<? super ResolvedDependency> spec, boolean includeTransitive = true) {
        Set<ResolvedDependency> dependencies = findMatchingDependencies(spec,
                project.configurations.runtime.resolvedConfiguration.firstLevelModuleDependencies, includeTransitive)
        dependencies.collect { it.moduleArtifacts.file }.flatten().each { File file ->
            this.mainSpec.exclude(FilenameUtils.getName(file.path))
        }
        return this
    }

    /**
     * Include dependencies that match the provided spec.
     *
     * @param spec
     * @param includeTransitive include the transitive dependencies of any dependency that matches the spec.
     * @return
     */
    public DependencyFilter include(Spec<? super ResolvedDependency> spec, boolean includeTransitive = true) {
        Set<ResolvedDependency> dependencies = findMatchingDependencies(spec,
                project.configurations.runtime.resolvedConfiguration.firstLevelModuleDependencies, includeTransitive)
        dependencies.collect { it.moduleArtifacts.file }.flatten().each { File file ->
            this.mainSpec.include(FilenameUtils.getName(file.path))
        }
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
     * @param includeTransitive
     * @return
     */
    protected Set<ResolvedDependency> findMatchingDependencies(Closure spec,
                                                               Set<ResolvedDependency> dependencies,
                                                               boolean includeTransitive) {
        findMatchingDependencies(
                Specs.<? super ResolvedDependency>convertClosureToSpec(spec), dependencies, includeTransitive)
    }

    /**
     * Support method for querying the resolved dependency graph using maven/project coordinates
     * @param spec
     * @param dependencies
     * @param includeTransitive
     * @return
     */
    protected Set<ResolvedDependency> findMatchingDependencies(Spec<? super ResolvedDependency> spec,
                                                               Set<ResolvedDependency> dependencies,
                                                               boolean includeTransitive) {

        Set<ResolvedDependency> matched = []
        dependencies.each {
            if (spec.isSatisfiedBy(it)) {
                matched.add(it)
                if (includeTransitive) {
                    matched.addAll(findMatchingDependencies({true}, it.children, true))
                }
            }
            matched.addAll(findMatchingDependencies(spec, it.children, includeTransitive))
        }
        return matched
    }
}
