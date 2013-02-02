package org.gradle.api.plugins.shadow

import org.gradle.api.Project
import org.gradle.api.plugins.shadow.impl.ArtifactSet
import org.gradle.api.plugins.shadow.transformers.DontIncludeResourceTransformer
import org.gradle.api.plugins.shadow.transformers.IncludeResourceTransformer
import org.gradle.api.plugins.shadow.transformers.ManifestResourceTransformer
import org.gradle.api.plugins.shadow.transformers.ServiceFileTransformer
import org.gradle.api.plugins.shadow.transformers.Transformer

class ShadowTaskExtension {

    public static final NAME = "shadow"

    List<Transformer> transformers = [new ServiceFileTransformer(), new ManifestResourceTransformer()]
    ArtifactSet artifactSet = new ArtifactSet()

    String destinationDir = "${project.buildDir}/libs/"
    String baseName = "${project.archivesBaseName}-shadow-${project.version}"
    String extension = "jar"
    boolean stats = false
    boolean artifactAttached = true

    private final Project project

    ShadowTaskExtension(Project project) {
        this.project = project
    }

    File getShadowJar() {
        return new File(destinationDir, "$baseName.$extension")
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
