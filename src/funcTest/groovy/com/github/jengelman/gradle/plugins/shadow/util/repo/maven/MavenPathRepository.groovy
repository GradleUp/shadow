package com.github.jengelman.gradle.plugins.shadow.util.repo.maven


/**
 * A fixture for dealing with file Maven repositories.
 */
class MavenPathRepository implements MavenRepository {
    final File rootDir

    MavenPathRepository(File rootDir) {
        this.rootDir = rootDir
    }

    @Override
    URI getUri() {
        return rootDir.toURI()
    }

    @Override
    MavenFileModule module(String groupId, String artifactId, Object version = '1.0') {
        def artifactDir = rootDir.resolve("${groupId.replace('.', '/')}/$artifactId/$version")
        return new MavenFileModule(artifactDir, groupId, artifactId, version as String)
    }
}
