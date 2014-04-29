package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowCopy
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.initialization.ClassLoaderRegistry
import org.gradle.internal.classloader.FilteringClassLoader

class Shadow2Plugin implements Plugin<Project> {

    @Override
    void apply(Project project) {

        overrideClassLoader(project)
        project.plugins.apply(JavaPlugin)
        createShadowConfiguration(project)
        configureShadowTask(project)
    }

    private void overrideClassLoader(Project project) {
        FilteringClassLoader filter = ((ClassLoaderRegistry) project.getServices().get(ClassLoaderRegistry))
                .gradleApiClassLoader.parent
        filter.allowPackage('org.apache.tools.zip')
        filter.allowPackage('org.apache.commons.io')
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
