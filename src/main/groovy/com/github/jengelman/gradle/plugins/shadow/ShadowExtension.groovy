package com.github.jengelman.gradle.plugins.shadow

import org.gradle.api.Project
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.api.file.CopySpec
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication

class ShadowExtension {

    CopySpec applicationDistribution
    Project project

    ShadowExtension(Project project) {
        this.project = project
        applicationDistribution = project.copySpec {}
    }

    void component(MavenPublication publication) {

        publication.artifact(project.tasks.shadowJar)
        publication.pom { MavenPom pom ->
            pom.withXml { xml ->
                def dependenciesNode = xml.asNode().appendNode('dependencies')

                project.configurations.shadow.allDependencies.each {
                    if (! (it instanceof SelfResolvingDependency)) {
                        def dependencyNode = dependenciesNode.appendNode('dependency')
                        dependencyNode.appendNode('groupId', it.group)
                        dependencyNode.appendNode('artifactId', it.name)
                        dependencyNode.appendNode('version', it.version)
                        dependencyNode.appendNode('scope', 'runtime')
                    }
                }
            }
        }
    }
}
