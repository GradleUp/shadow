package com.github.jengelman.gradle.plugins.shadow.util

import com.github.jengelman.gradle.plugins.shadow.util.repo.maven.MavenFileModule
import groovy.transform.InheritConstructors
import org.apache.commons.io.IOUtils

@InheritConstructors
class AppendableMavenFileModule extends MavenFileModule {

    Map<String, Map<String, String>> contents = [:].withDefault { [:] }
    Map<String, File> files = [:]

    AppendableMavenFileModule use(File file) {
        return use('', file)
    }

    AppendableMavenFileModule use(String classifier, File file) {
        files[classifier] = file
        return this
    }

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
        String classifier = (String) artifact['classifier'] ?: ''
        if (files.containsKey(classifier)) {
            publishWithStream(artifactFile) { OutputStream os ->
                IOUtils.copy(files[classifier].newInputStream(), os)
            }
        } else {
            publishWithStream(artifactFile) { OutputStream os ->
                writeJar(os, contents[classifier])
            }
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
