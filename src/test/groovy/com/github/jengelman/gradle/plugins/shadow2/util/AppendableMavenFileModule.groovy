package com.github.jengelman.gradle.plugins.shadow2.util

import com.github.jengelman.gradle.testkit.repo.maven.MavenFileModule
import groovy.transform.InheritConstructors

@InheritConstructors
class AppendableMavenFileModule extends MavenFileModule {

    Map<String, Map<String, String>> contents = [:].withDefault { [:] }

    AppendableMavenFileModule insertFile(String path, String content) {
        insertFile('', path, content)
        return this
    }

    AppendableMavenFileModule insertFile(String classifier, String path, String content) {
        contents[classifier][path] = content
        return this
    }

    @Override
    File publishArtifact(Map<String, ?> artifact) {
        def artifactFile = artifactFile(artifact)
        if (type == 'pom') {
            return artifactFile
        }
        publishWithStream(artifactFile) { OutputStream os ->
            writeJar(os, contents[(String) artifact['classifier'] ?: ''])
        }
        return artifactFile
    }

    void writeJar(OutputStream os, Map<String, String> contents) {
        if (contents) {
            JarBuilder builder = new JarBuilder(os)
            contents.each { path, content ->
                builder.withFile(path, content)
            }
            builder.build()
        }
    }

    /**
     * Adds an additional artifact to this module.
     * @param options Can specify any of: type or classifier
     */
    AppendableMavenFileModule artifact(Map<String, ?> options) {
        artifacts << options
        return this
    }

}
