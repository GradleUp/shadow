package com.github.jengelman.gradle.plugins.shadow.tasks

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.internal.DefaultDependencyFilter
import com.github.jengelman.gradle.plugins.shadow.internal.DefaultZipCompressor
import com.github.jengelman.gradle.plugins.shadow.internal.DependencyFilter
import com.github.jengelman.gradle.plugins.shadow.internal.GradleVersionUtil
import com.github.jengelman.gradle.plugins.shadow.internal.ZipCompressor
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.ConfigureUtil

class ShadowJar extends Jar implements ShadowSpec {

    List<Transformer> transformers = []
    List<Relocator> relocators = []
    List<Configuration> configurations = []
    DependencyFilter dependencyFilter

    private final ShadowStats shadowStats = new ShadowStats()
    private final GradleVersionUtil versionUtil

    ShadowJar() {
        versionUtil = new GradleVersionUtil(project.gradle.gradleVersion)
        dependencyFilter = new DefaultDependencyFilter(project)
        manifest = new DefaultInheritManifest(getServices().get(FileResolver))
    }

    @Override
    // This should really return InheritManifest but cannot due to https://jira.codehaus.org/browse/GROOVY-5418
    // TODO - change return type after upgrade to Gradle 2
    public DefaultInheritManifest getManifest() {
        return super.getManifest()
    }

    @Override
    protected CopyAction createCopyAction() {
        DocumentationRegistry documentationRegistry = getServices().get(DocumentationRegistry)
        return new ShadowCopyAction(getArchivePath(), getInternalCompressor(), documentationRegistry,
                transformers, relocators, rootPatternSet, shadowStats)
    }

    protected ZipCompressor getInternalCompressor() {
        versionUtil.getInternalCompressor(entryCompression, this)
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
        versionUtil.getRootPatternSet(mainSpec)
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
            path = rootPath
        }
    }

    /**
     * Syntactic sugar for merging service files in JARs
     * @return
     */
    ShadowJar mergeServiceFiles(Closure configureClosure) {
        transform(ServiceFileTransformer, configureClosure)
    }

    /**
     * Syntactic sugar for merging Groovy extension module descriptor files in JARs
     * @return
     */
    ShadowJar mergeGroovyExtensionModules() {
        transform(GroovyExtensionModuleTransformer)
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
