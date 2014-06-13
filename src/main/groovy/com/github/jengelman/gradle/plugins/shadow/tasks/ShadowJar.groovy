package com.github.jengelman.gradle.plugins.shadow.tasks

import static org.gradle.api.tasks.bundling.ZipEntryCompression.*

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.internal.DefaultZipCompressor
import com.github.jengelman.gradle.plugins.shadow.internal.ZipCompressor
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import org.apache.commons.io.FilenameUtils
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.ConfigureUtil

class ShadowJar extends Jar {

    List<Transformer> transformers = []
    List<Relocator> relocators = []

    private final ShadowStats shadowStats = new ShadowStats()

    @Override
    protected CopyAction createCopyAction() {
        DocumentationRegistry documentationRegistry = getServices().get(DocumentationRegistry)
        return new ShadowCopyAction(getArchivePath(), getCustomCompressor(), documentationRegistry,
                transformers, relocators, (PatternSet) mainSpec.getPatternSet(), shadowStats)
    }

    @TaskAction
    protected void copy() {
        super.copy()
        logger.info(shadowStats.toString())
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

    /**
     * Add a Transformer instance for modifying JAR resources and configure.
     * @param clazz
     * @param c
     * @return
     */
    ShadowJar transform(Class<? super Transformer> clazz, Closure c = null) {
        Transformer transformer = clazz.newInstance()
        if (c) {
            ConfigureUtil.configure(c, transformer)
        }
        transformers << transformer
        return this
    }

    /**
     * Syntax sugar for merging service files in JARs
     * @return
     */
    ShadowJar mergeServiceFiles() {
        transform(ServiceFileTransformer)
    }

    /**
     * Syntax sugar for merging service files in JARs
     * @return
     */
    ShadowJar append(String resourcePath) {
        transform(AppendingTransformer) {
            resource = resourcePath
        }
    }

    /**
     * Append content to the JAR Manifest created by the Jar task.
     * @param configureClosure
     * @return
     */
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
            this.exclude(FilenameUtils.getName(file.path))
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
            this.include(FilenameUtils.getName(file.path))
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
     * Add a class relocator that maps each class in the pattern to the provided destination
     * @param pattern
     * @param destination
     * @param configure
     * @return
     */
    public ShadowJar relocate(String pattern, String destination, Closure configure = null) {
        SimpleRelocator relocator = new SimpleRelocator(pattern, destination, [], [])
        if (configure) {
            ConfigureUtil.configure(configure, relocator)
        }
        relocators << relocator
        return this
    }

    /**
     * Add a relocator instance
     * @param relocator
     * @return
     */
    public ShadowJar relocate(Relocator relocator) {
        relocators << relocator
        return this
    }

    /**
     * Add a relocator of the provided class and configure
     * @param relocatorClass
     * @param configure
     * @return
     */
    public ShadowJar relocate(Class<? super Relocator> relocatorClass, Closure configure = null) {
        Relocator relocator = relocatorClass.newInstance()
        if (configure) {
            ConfigureUtil.configure(configure, relocator)
        }
        relocators << relocator
        return this
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
