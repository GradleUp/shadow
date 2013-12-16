package org.gradle.api.plugins.shadow

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.shadow.impl.ArchiveFilter
import org.gradle.api.plugins.shadow.impl.ArchiveRelocation
import org.gradle.api.plugins.shadow.impl.ArtifactSet
import org.gradle.api.plugins.shadow.transformers.Transformer

class ShadowTaskExtension {

    public static final NAME = "shadow"

    List<Transformer> transformers = []
    List<ArchiveFilter> filters = []
    List<ArchiveRelocation> relocations = []
    ArtifactSet artifactSet

    String destinationDir = "${project.buildDir}/libs/"
    String baseName = null
    String extension = 'jar'
    String groupFilter
    String classifier = 'shadow'
    boolean stats = false
    boolean artifactAttached = true
    boolean reducePom = false

    File outputFile

    private final Project project

    ShadowTaskExtension(Project project) {
        this.project = project
    }

    String getOutputJarBaseName() {
        if(baseName) {
            baseName
        } else {
            project.archivesBaseName
        }
    }

    File getShadowJar() {
        return outputFile ?: new File(destinationDir, "${outputJarBaseName}${jarVersion}${jarClassifier}.$extension")
    }

    String getJarVersion() {
        project.version ? "-${project.version}" : ''
    }

    String getJarClassifier() {
        artifactAttached ? "-${classifier}" : ''
    }

    String getSignedLibsDir() {
        return destinationDir + 'signedLibs/'
    }

    ShadowTaskExtension artifactSet(Closure c) {
        artifactSet = artifactSet ?: new ArtifactSet()
        c.delegate = artifactSet
        c.resolveStrategy = Closure.DELEGATE_ONLY
        c()
        this
    }

    ShadowTaskExtension filter(String artifact, Closure c) {
        if (!artifact) throw new GradleException('Must specify artifact for filter!')
        ArchiveFilter filter = filters.find { it.artifact == artifact } ?: new ArchiveFilter(artifact: artifact)
        c.delegate = filter
        c.resolveStrategy = Closure.DELEGATE_ONLY
        c()
        if (!filters.contains(filter)) {
            filters << filter
        }
        this
    }

    ShadowTaskExtension transformer(Class transformerClass, Closure c = null) {
        if (!transformerClass) throw new GradleException('Must specify transformer impl class!')
        Transformer transformer = transformerClass.newInstance()
        if (c) {
            c.delegate = transformer
            c.resolveStrategy = Closure.DELEGATE_ONLY
            c()
        }
        transformers << transformer
        this
    }

    ShadowTaskExtension relocation(Closure c) {
        ArchiveRelocation relocation = new ArchiveRelocation()
        if (c) {
            c.delegate = relocation
            c.resolveStrategy = Closure.DELEGATE_ONLY
            c()
        }
        relocations << relocation
        this
    }

    ShadowTaskExtension include(String s) {
        filter('*:*') {
            include s
        }
    }

    ShadowTaskExtension exclude(String s) {
        filter('*:*') {
            exclude s
        }
    }

}
