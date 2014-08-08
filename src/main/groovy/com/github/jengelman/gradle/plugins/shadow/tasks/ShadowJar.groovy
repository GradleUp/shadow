package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.internal.DefaultDependencyFilter
import com.github.jengelman.gradle.plugins.shadow.internal.DependencyFilter
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.ConfigureUtil

class ShadowJar extends Jar implements ShadowSpec {

    List<Transformer> transformers = []
    List<Relocator> relocators = []
    List<Configuration> configurations = []
    DependencyFilter dependencyFilter

    private final ShadowStats shadowStats = new ShadowStats()

    ShadowJar() {
        dependencyFilter = new DefaultDependencyFilter(project)
    }

    @Override
    protected CopyAction createCopyAction() {
        DocumentationRegistry documentationRegistry = getServices().get(DocumentationRegistry)
        return new ShadowCopyAction(getArchivePath(), getCompressor(), documentationRegistry,
                transformers, relocators, rootPatternSet, shadowStats)
    }

    @TaskAction
    protected void copy() {
        from(includedDependencies)
        super.copy()
        logger.info(shadowStats.toString())
    }

    @InputFiles @Optional
    public FileCollection getIncludedDependencies() {
        dependencyFilter.resolve(configurations)
    }

    /**
     * Utility method for assisting between changes in Gradle 1.12 and 2.x
     * @return
     */
    protected PatternSet getRootPatternSet() {
        // Gradle 1.12 class exposes patternSet on the spec
        if (mainSpec.respondsTo('getPatternSet')) {
            return mainSpec.getPatternSet()
        // Gradle 2.x moves it to the spec resolver.
        } else {
            return mainSpec.buildRootResolver().getPatternSet()
        }
    }

    /**
     * Configure inclusion/exclusion of module & project dependencies into uber jar
     * @param c
     * @return
     */
    ShadowJar dependencies(Closure c) {
        ConfigureUtil.configure(c, dependencyFilter)
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
        ConfigureUtil.configure(c, transformer)
        transformers << transformer
        return this
    }

    /**
     * Add a preconfigured transformer instance
     * @param transformer
     * @return
     */
    ShadowJar transform(Transformer transformer) {
        transformers << transformer
        return this
    }

    /**
     * Syntactic sugar for merging service files in JARs
     * @return
     */
    ShadowJar mergeServiceFiles() {
        transform(ServiceFileTransformer)
    }

    /**
     * Syntactic sugar for merging service files in JARs
     * @return
     */
    ShadowJar mergeServiceFiles(String rootPath) {
        transform(ServiceFileTransformer) {
            setIncludes(["${rootPath}/**"])
        }
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
    ShadowJar appendManifest(Closure configureClosure) {
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
    ShadowJar relocate(String pattern, String destination, Closure configure = null) {
        SimpleRelocator relocator = new SimpleRelocator(pattern, destination, [], [])
        ConfigureUtil.configure(configure, relocator)
        relocators << relocator
        return this
    }

    /**
     * Add a relocator instance
     * @param relocator
     * @return
     */
    ShadowJar relocate(Relocator relocator) {
        relocators << relocator
        return this
    }

    /**
     * Add a relocator of the provided class and configure
     * @param relocatorClass
     * @param configure
     * @return
     */
    ShadowJar relocate(Class<? super Relocator> relocatorClass, Closure configure = null) {
        Relocator relocator = (Relocator) relocatorClass.newInstance()
        ConfigureUtil.configure(configure, relocator)
        relocators << relocator
        return this
    }

}
