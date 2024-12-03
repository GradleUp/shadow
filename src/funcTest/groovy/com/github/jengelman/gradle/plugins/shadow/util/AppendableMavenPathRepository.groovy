package com.github.jengelman.gradle.plugins.shadow.util

import com.github.jengelman.gradle.plugins.shadow.util.repo.maven.MavenPathRepository
import groovy.transform.InheritConstructors

@InheritConstructors
class AppendableMavenPathRepository extends MavenPathRepository {

    @Override
    AppendableMavenFileModule module(String groupId, String artifactId, Object version = '1.0') {
        def artifactDir = rootDir.resolve("${groupId.replace('.', '/')}/$artifactId/$version")
        return new AppendableMavenFileModule(artifactDir, groupId, artifactId, version as String)
    }

}
