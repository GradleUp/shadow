package com.github.jengelman.gradle.plugins.shadow.util.repo.maven

class MavenDependency {
    String groupId
    String artifactId
    String version
    String classifier
    String type

    MavenDependency hasType(def type) {
        assert this.type == type
        return this
    }

    @Override
    public String toString() {
        return String.format("MavenDependency %s:%s:%s:%s@%s", groupId, artifactId, version, classifier, type)
    }
}
