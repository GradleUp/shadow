package com.github.jengelman.gradle.plugins.shadow.support

import com.github.jengelman.gradle.plugins.integration.MavenFileModule
import groovy.transform.InheritConstructors

@InheritConstructors
class AppendableMavenFileModule extends MavenFileModule {

    Map<String, String> contents = [:]

    AppendableMavenFileModule insertFile(String path, String content) {
        contents[path] = content
        return this
    }

    @Override
    File publishArtifact(Map<String, ?> artifact) {
        def artifactFile = artifactFile(artifact)
        if (type == 'pom') {
            return artifactFile
        }
        publishWithStream(artifactFile) { OutputStream os ->
            writeJar(os)

        }
        return artifactFile
    }

    void writeJar(OutputStream os) {
        JarBuilder builder = new JarBuilder(os)
        contents.each { path, contents ->
            builder.withFile(path, contents)
        }
        builder.build()
    }

}
