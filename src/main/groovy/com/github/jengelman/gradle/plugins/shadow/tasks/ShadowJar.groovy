package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.internal.ArtifactFilter
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import org.apache.commons.io.FilenameUtils
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
    private final ArtifactFilter artifactFilter

    ShadowJar() {
        artifactFilter = new ArtifactFilter(project)
    }

    @Override
    protected CopyAction createCopyAction() {
        DocumentationRegistry documentationRegistry = getServices().get(DocumentationRegistry)
        return new ShadowCopyAction(getArchivePath(), getCompressor(), documentationRegistry,
                transformers, relocators, (PatternSet) mainSpec.getPatternSet(), artifactFilter.patternSet, shadowStats)
    }

    @TaskAction
    protected void copy() {
        super.copy()
        logger.info(shadowStats.toString())
    }

    /**
     * Configure inclusion/exclusion of Jar artifacts into uber jar
     * @param c
     * @return
     */
    ShadowJar artifacts(Closure c) {
        ConfigureUtil.configure(c, artifactFilter)
        return this
    }

    /**
     * Add a Transformer instance for modifying JAR resources and configure.
     * @param clazz
     * @param c
     * @return
     */
    ShadowJar transform(Class<? super Transformer> clazz, Closure c = null) {
        Transformer transformer = (Transformer) clazz.newInstance()
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
        Relocator relocator = (Relocator) relocatorClass.newInstance()
        if (configure) {
            ConfigureUtil.configure(configure, relocator)
        }
        relocators << relocator
        return this
    }

}
