package com.github.jengelman.gradle.plugins.shadow.tasks

import static org.gradle.api.tasks.bundling.ZipEntryCompression.*

import com.github.jengelman.gradle.plugins.shadow.internal.DefaultZipCompressor
import com.github.jengelman.gradle.plugins.shadow.internal.ZipCompressor
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.bundling.Jar
import org.gradle.util.ConfigureUtil

class ShadowJar extends Jar {

    List<Transformer> transformers = []
    List<Relocator> relocators = []

    @Override
    protected CopyAction createCopyAction() {
        DocumentationRegistry documentationRegistry = getServices().get(DocumentationRegistry)
        return new ShadowCopyAction(getArchivePath(), getCustomCompressor(), documentationRegistry, transformers, relocators)
    }

    protected ZipCompressor getCustomCompressor() {
        switch (entryCompression) {
            case DEFLATED:
                return new DefaultZipCompressor(zip64, ZipOutputStream.DEFLATED)
            case STORED:
                return new DefaultZipCompressor(zip64, ZipOutputStream.STORED)
            default:
                throw new IllegalArgumentException(String.format('Unknown Compression type %s', entryCompression))
        }
    }

    ShadowJar transformer(Class<? extends Transformer> clazz, Closure c = null) {
        Transformer transformer = clazz.newInstance()
        if (c) {
            c.delegate = transformer
            c.resolveStrategy = Closure.DELEGATE_FIRST
            c(transformer)
        }
        transformers << transformer
        return this
    }

    public ShadowJar appendManifest(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getManifest())
        return this
    }

    /**
     * Exclude dependencies that match the provided spec.
     *
     * @param spec
     * @param includeTransitive exclude the transitive dependencies of any dependency that matches the spec.
     * @return
     */
    public ShadowJar exclude(Spec<? super ResolvedDependency> spec, boolean includeTransitive = true) {
        Set<ResolvedDependency> dependencies = findMatchingDependencies(spec,
                project.configurations.runtime.resolvedConfiguration.firstLevelModuleDependencies, includeTransitive)
        dependencies.collect { it.moduleArtifacts.file }.flatten().each { File file ->
            this.exclude(file.path.substring(file.path.lastIndexOf('/')+1)) //Get just the file name
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
    public ShadowJar include(Spec<? super ResolvedDependency> spec, boolean includeTransitive = true) {
        Set<ResolvedDependency> dependencies = findMatchingDependencies(spec,
                project.configurations.runtime.resolvedConfiguration.firstLevelModuleDependencies, includeTransitive)
        dependencies.collect { it.moduleArtifacts.file }.flatten().each { File file ->
            this.include(file.path.substring(file.path.lastIndexOf('/')+1))
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

    public ShadowJar relocate(String pattern, String destination, Closure configure = null) {
        SimpleRelocator relocator = new SimpleRelocator(pattern, destination, [], [])
        if (configure) {
            ConfigureUtil.configure(configure, relocator)
        }
        relocators << relocator
        return this
    }

    protected Set<ResolvedDependency> findMatchingDependencies(Closure spec,
                                                             Set<ResolvedDependency> dependencies,
                                                             boolean includeTransitive) {
        findMatchingDependencies(
                Specs.<? super ResolvedDependency>convertClosureToSpec(spec), dependencies, includeTransitive)
    }

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
