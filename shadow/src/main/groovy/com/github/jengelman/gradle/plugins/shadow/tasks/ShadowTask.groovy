package com.github.jengelman.gradle.plugins.shadow.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.artifacts.ResolvedArtifact
import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.filter.Filter
import com.github.jengelman.gradle.plugins.shadow.filter.SimpleFilter
import com.github.jengelman.gradle.plugins.shadow.impl.ArchiveFilter
import com.github.jengelman.gradle.plugins.shadow.impl.ArchiveRelocation
import com.github.jengelman.gradle.plugins.shadow.impl.ArtifactId
import com.github.jengelman.gradle.plugins.shadow.impl.ArtifactSelector
import com.github.jengelman.gradle.plugins.shadow.impl.ArtifactSet
import com.github.jengelman.gradle.plugins.shadow.impl.Caster
import com.github.jengelman.gradle.plugins.shadow.impl.DefaultCaster
import com.github.jengelman.gradle.plugins.shadow.impl.ShadowRequest
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class ShadowTask extends DefaultTask {

    static final String NAME = "shadowJar"
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
        List<File> selfResolvingDependencies = selfResolvingDependencies*.resolve().flatten().findAll { File file ->
            selector.isSelected(file)
        }
        resolvedFiles + selfResolvingDependencies
    }

    List<SelfResolvingDependency> getSelfResolvingDependencies() {
        (List<SelfResolvingDependency>) project.configurations.runtime.allDependencies.findAll {
            it instanceof SelfResolvingDependency
        }.asList()
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
