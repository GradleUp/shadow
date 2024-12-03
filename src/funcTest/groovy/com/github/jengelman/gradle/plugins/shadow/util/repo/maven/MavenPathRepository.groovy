package com.github.jengelman.gradle.plugins.shadow.util.repo.maven

import java.nio.file.Path


/**
 * A fixture for dealing with file Maven repositories.
 */
class MavenPathRepository implements MavenRepository {
    final Path rootDir

    MavenPathRepository(Path rootDir) {
        this.rootDir = rootDir
    }

    @Override
    URI getUri() {
        return rootDir.toFile().toURI()
    }

    @Override
    MavenFileModule module(String groupId, String artifactId, Object version = '1.0') {
        def artifactDir = rootDir.resolve("${groupId.replace('.', '/')}/$artifactId/$version")
        return new MavenFileModule(artifactDir, groupId, artifactId, version as String)
    }
}
