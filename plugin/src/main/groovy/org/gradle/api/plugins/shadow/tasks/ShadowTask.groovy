package org.gradle.api.plugins.shadow.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.RelativePath
import org.gradle.api.plugins.shadow.ShadowStats
import org.gradle.api.plugins.shadow.impl.ArtifactSelector
import org.gradle.api.plugins.shadow.impl.ArtifactSet
import org.gradle.api.plugins.shadow.impl.Caster
import org.gradle.api.plugins.shadow.impl.DefaultCaster
import org.gradle.api.plugins.shadow.impl.ShadowRequest
import org.gradle.api.plugins.shadow.relocation.Relocator
import org.gradle.api.plugins.shadow.transformers.Transformer
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class ShadowTask extends DefaultTask {

    static final String NAME = "shadow"
    static final String DESC = "Combines all classpath resources into a single jar."

    @OutputFile
    File outputJar = project.shadow.shadowJar

    List<Transformer> transformers = project.shadow.transformers
    List<Relocator> relocators = []

    boolean statsEnabled

    List<RelativePath> existingPaths = []

    ShadowStats stats
    Caster caster

    @TaskAction
    void shadow() {

        logger.info "${NAME.capitalize()} - start"
        initStats()

        caster = new DefaultCaster()

        ShadowRequest shadow = new ShadowRequest()
        shadow.stats = stats
        shadow.uberJar = outputJar
        shadow.relocators = []
        shadow.filters = []
        shadow.resourceTransformers = project.shadow.transformers
        shadow.shadeSourcesContent = false
        shadow.jars = jars

        caster.cast(shadow)

        logger.info "${NAME.capitalize()} - finish"
        printStats()
    }

    private void initStats() {
        statsEnabled = project.shadow.stats
        if (statsEnabled) {
            stats = new ShadowStats()
            stats.jarCount = jars.size()
            logger.info "${NAME.capitalize()} - total jars [${stats.jarCount}]"
        }
    }

    private void printStats() {
        if (statsEnabled) {
            stats.printStats()
        }
    }

    List<File> getJars() {
        println "Getting Jars"
        ArtifactSelector selector = initSelector()
        println "Selector: $selector"
        getArtifacts(selector) + getDependencies(selector)
    }

    @InputFiles
    List<File> getArtifacts(ArtifactSelector selector) {
        println "Getting artifacts"
        project.configurations.runtime.artifacts.files as List
    }

    List<File> getSignedJars() {
        signedCompileJars + signedRuntimeJars
    }

    List<File> getSignedCompileJars() {
        project.configurations.signedCompile.resolve() as List
    }

    List<File> getSignedRuntimeJars() {
        project.configurations.signedRuntime.resolve() as List
    }

    List<ResolvedArtifact> getResolvedSignedArtifacts() {
        ['signedCompile', 'signedRuntime'].collect {
            getResolvedArtifactsFor(it)
        }.flatten().unique()
    }

    List<ResolvedArtifact> getAllResolvedArtifacts() {
        getResolvedArtifactsFor('runtime')
    }

    List<ResolvedArtifact> getResolvedArtifactsFor(String configuration) {
        project.configurations."$configuration".resolvedConfiguration.resolvedArtifacts as List
    }

    @InputFiles
    List<File> getDependencies(ArtifactSelector selector) {

        List<ResolvedArtifact> resolvedConfiguration = allResolvedArtifacts - resolvedSignedArtifacts
        println "Resolved Configuration: $resolvedConfiguration"
        List<File> resolvedFiles = resolvedConfiguration.findAll { resolvedArtifact ->
            selector.isSelected(resolvedArtifact)
        }.collect { resolvedArtifact ->
            resolvedArtifact.file
        }
        println "Resolved Files: $resolvedFiles"
        resolvedFiles
    }

    ArtifactSelector initSelector() {
        new ArtifactSelector(((PublishArtifactSet) project.configurations.runtime.artifacts),
                ((ArtifactSet) project.shadow.artifactSet),
                project.group as String)
    }
}
