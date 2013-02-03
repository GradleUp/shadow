package org.gradle.api.plugins.shadow

import org.gradle.api.Project
import org.gradle.api.plugins.shadow.impl.ArtifactSet
import org.gradle.api.plugins.shadow.transformers.ManifestResourceTransformer
import org.gradle.api.plugins.shadow.transformers.ServiceFileTransformer
import org.gradle.api.plugins.shadow.transformers.Transformer

class ShadowTaskExtension {

    public static final NAME = "shadow"

    List<Transformer> transformers = [new ServiceFileTransformer(), new ManifestResourceTransformer()]
    ArtifactSet artifactSet = new ArtifactSet()

    String destinationDir = "${project.buildDir}/libs/"
    String baseName = null
    String extension = "jar"
    boolean stats = false
    boolean artifactAttached = true

    private final Project project

    ShadowTaskExtension(Project project) {
        this.project = project
    }

    String getOutputJarBaseName() {
        if(baseName) {
            baseName
        } else if (artifactAttached) {
            "${project.archivesBaseName}-shadow"
        } else {
            project.archivesBaseName
        }
    }

    File getShadowJar() {
        return new File(destinationDir, "${outputJarBaseName}-${project.version}.$extension")
    }

    String getSignedLibsDir() {
        return destinationDir + 'signedLibs/'
    }

    ShadowTaskExtension artifactSet(Closure c) {
        c.delegate = artifactSet
        c()
        this
    }

}
