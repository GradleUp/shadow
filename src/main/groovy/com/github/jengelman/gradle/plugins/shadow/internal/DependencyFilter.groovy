package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.specs.Spec

interface DependencyFilter {

    /**
     * Resolve a Configuration against the include/exclude rules in the filter
     * @param configuration
     * @return
     */
    FileCollection resolve(Configuration configuration)

    /**
     * Resolve all Configurations against the include/exclude ruels in the filter and combine the results
     * @param configurations
     * @return
     */
    FileCollection resolve(Collection<Configuration> configurations)

    /**
     * Exclude dependencies that match the provided spec.
     *
     * @param spec
     * @return
     */
    DependencyFilter exclude(Spec<? super ResolvedDependency> spec)

    /**
     * Include dependencies that match the provided spec.
     *
     * @param spec
     * @return
     */
    DependencyFilter include(Spec<? super ResolvedDependency> spec)

    /**
     * Create a spec that matches the provided project notation on group, name, and version
     * @param notation
     * @return
     */
    Spec<? super ResolvedDependency> project(Map<String, ?> notation)

    /**
     * Create a spec that matches the default configuration for the provided project path on group, name, and version
     *
     * @param notation
     * @return
     */
    Spec<? super ResolvedDependency> project(String notation)

    /**
     * Create a spec that matches dependencies using the provided notation on group, name, and version
     * @param notation
     * @return
     */
    Spec<? super ResolvedDependency> dependency(Object notation)

    /**
     * Create a spec that matches the provided dependency on group, name, and version
     * @param dependency
     * @return
     */
    Spec<? super ResolvedDependency> dependency(Dependency dependency)

    /**
     * Create a spec that matches the provided closure
     * @param spec
     * @return
     */
    Spec<? super ResolvedDependency> dependency(Closure spec)
}
