package org.gradle.api.plugins.shadow.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.plugins.shadow.ShadowStats
import org.gradle.api.plugins.shadow.filter.Filter
import org.gradle.api.plugins.shadow.filter.SimpleFilter
import org.gradle.api.plugins.shadow.impl.ArchiveFilter
import org.gradle.api.plugins.shadow.impl.ArchiveRelocation
import org.gradle.api.plugins.shadow.impl.ArtifactId
import org.gradle.api.plugins.shadow.impl.ArtifactSelector
import org.gradle.api.plugins.shadow.impl.ArtifactSet
import org.gradle.api.plugins.shadow.impl.Caster
import org.gradle.api.plugins.shadow.impl.DefaultCaster
import org.gradle.api.plugins.shadow.impl.ShadowRequest
import org.gradle.api.plugins.shadow.relocation.Relocator
import org.gradle.api.plugins.shadow.relocation.SimpleRelocator
import org.gradle.api.plugins.shadow.transformers.Transformer
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class ShadowTask extends DefaultTask {

    static final String NAME = "shadow"
    static final String DESC = "Combines all classpath resources into a single jar."

    List<Transformer> transformers = project.shadow.transformers

    boolean statsEnabled

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
        shadow.relocators = relocators
        shadow.filters = filters
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

    @OutputFile
    File getOutputJar() {
        project.shadow.shadowJar
    }

    @InputFiles
    List<File> getJars() {
        ArtifactSelector selector = initSelector()
        getArtifacts(selector) + getDependencies(selector)
    }

    List<File> getArtifacts(ArtifactSelector selector) {
        List<File> artifacts = project.configurations.runtime.artifacts.files as List
        artifacts = renameOriginalArtifacts(artifacts)
        artifacts
    }

    List<File> renameOriginalArtifacts(List<File> artifacts) {
        if (!project.shadow.artifactAttached &&
                (!project.shadow.baseName ||
                        project.shadow.baseName == project.archivesBaseName)) {
            return artifacts.collect { artifact ->
                def newFile = new File(artifact.parent, "original-${artifact.name}")
                artifact.renameTo(newFile)
                newFile
            }
        }
        artifacts
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

    List<ResolvedArtifact> getResolvedArtifactsToShadow() {
        allResolvedArtifacts - resolvedSignedArtifacts
    }

    List<ResolvedArtifact> getResolvedArtifactsFor(String configuration) {
        project.configurations."$configuration".resolvedConfiguration.resolvedArtifacts as List
    }

    List<File> getDependencies(ArtifactSelector selector) {

        List<ResolvedArtifact> resolvedConfiguration = resolvedArtifactsToShadow
        List<File> resolvedFiles = resolvedConfiguration.findAll { resolvedArtifact ->
            selector.isSelected(resolvedArtifact) && resolvedArtifact.type != 'pom'
        }.collect { resolvedArtifact ->
            resolvedArtifact.file
        }
        resolvedFiles
    }

    ArtifactSelector initSelector() {
        new ArtifactSelector(((PublishArtifactSet) project.configurations.runtime.artifacts),
                ((ArtifactSet) project.shadow.artifactSet),
                project.shadow.groupFilter as String)
    }

    List<Filter> getFilters() {
        //TODO this doesn't include the project artifact
        Map<ResolvedArtifact, ArtifactId> artifacts = resolvedArtifactsToShadow.inject([:]) { map, artifact ->
            map[artifact] = new ArtifactId(artifact)
            map
        }
        List<ArchiveFilter> archiveFilters = project.shadow.filters
        List<Filter> configuredFilters = archiveFilters.collect { archiveFilter ->
            ArtifactId pattern = new ArtifactId((String) archiveFilter.artifact)
            List<File> jars = artifacts.findAll { ResolvedArtifact resolvedArtifact, ArtifactId artifactId ->
                artifactId.matches(pattern)
            }.collect { entry ->
                entry.key.file
                //TODO implementation for createSourcesJar
            }
            if (jars) {
                new SimpleFilter( jars, archiveFilter.includes, archiveFilter.excludes)
            }
        }.findAll { it }
        //TODO minijar filter
        configuredFilters
    }

    List<Relocator> getRelocators() {
        project.shadow.relocations.collect { ArchiveRelocation relocation ->
            new SimpleRelocator(relocation.pattern, relocation.shadedPattern, relocation.includes, relocation.excludes)
        }
    }
}
