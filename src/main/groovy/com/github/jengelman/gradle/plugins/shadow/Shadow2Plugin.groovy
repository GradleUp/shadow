package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopy
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention

class Shadow2Plugin implements Plugin<Project> {

    @Override
    void apply(Project project) {

        project.plugins.apply(JavaPlugin)
        createShadowConfiguration(project)
        configureShadowTask(project)
    }

    private void createShadowConfiguration(Project project) {
        project.configurations.create('shadow')
    }

    private void configureShadowTask(Project project) {
        JavaPluginConvention convention = project.convention.getPlugin(JavaPluginConvention)
        ShadowCopy shadow = project.tasks.create('shadowJar', ShadowCopy)
        shadow.conventionMapping.with {
            map('classifier') {
                'shadow'
            }
        }
        shadow.from(convention.sourceSets.main.output)
        shadow.from(project.configurations.runtime)

        ArchivePublishArtifact shadowArtifact = new ArchivePublishArtifact(shadow)
        project.configurations.shadow.artifacts.add(shadowArtifact)
    }
}
