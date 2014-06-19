package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.java.JavaLibrary
import org.gradle.api.plugins.JavaPluginConvention

class ShadowJavaPlugin implements Plugin<Project> {

    static final String SHADOW_JAR_TASK_NAME = 'shadowJar'
    static final String SHADOW_COMPONENT_NAME = 'shadow'
    static final String SHADOW_GROUP = 'Shadow'

    @Override
    void apply(Project project) {
        configureShadowTask(project)
    }

    private void configureShadowTask(Project project) {
        JavaPluginConvention convention = project.convention.getPlugin(JavaPluginConvention)
        ShadowJar shadow = project.tasks.create(SHADOW_JAR_TASK_NAME, ShadowJar)
        shadow.group = SHADOW_GROUP
        shadow.description = 'Create a combined JAR of project and runtime dependencies'
        shadow.conventionMapping.with {
            map('classifier') {
                'all'
            }
            map('manifest') {
                project.tasks.jar.manifest
            }
        }
        shadow.doFirst {
            manifest.attributes 'Class-Path': project.configurations.
                    findByName(ShadowBasePlugin.CONFIGURATION_NAME).files.collect { "lib/${it.name}" }.join(' ')
        }
        shadow.from(convention.sourceSets.main.output)
        shadow.from(project.configurations.runtime)
        shadow.exclude('META-INF/INDEX.LIST', 'META-INF/*.SF', 'META-INF/*.DSA', 'META-INF/*.RSA')

        PublishArtifact shadowArtifact = project.artifacts.add(ShadowBasePlugin.CONFIGURATION_NAME, shadow)
        project.components.add(new ShadowJavaLibrary(shadowArtifact, project.configurations.shadow.allDependencies))
    }

    class ShadowJavaLibrary extends JavaLibrary {

        ShadowJavaLibrary(PublishArtifact jarArtifact, DependencySet runtimeDependencies) {
            super(jarArtifact, runtimeDependencies)
        }

        @Override
        String getName() {
            return SHADOW_COMPONENT_NAME
        }
    }
}
