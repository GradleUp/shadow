package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.internal.ApplicationConfigurer
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.java.JavaLibrary
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention

class ShadowPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {

        project.plugins.apply(JavaPlugin)
        project.extensions.create('shadow', ShadowExtension, project)
        createShadowConfiguration(project)
        configureShadowTask(project)
        project.plugins.withType(ApplicationPlugin) {
            new ApplicationConfigurer(project.tasks.findByName('shadowJar')).execute(project)
        }
    }

    private void createShadowConfiguration(Project project) {
        project.configurations.create('shadow')
    }

    private void configureShadowTask(Project project) {
        JavaPluginConvention convention = project.convention.getPlugin(JavaPluginConvention)
        ShadowJar shadow = project.tasks.create('shadowJar', ShadowJar)
        shadow.conventionMapping.with {
            map('classifier') {
                'all'
            }
            map('manifest') {
                project.tasks.jar.manifest
            }
        }
        shadow.from(convention.sourceSets.main.output)
        shadow.from(project.configurations.runtime)
        shadow.exclude('META-INF/INDEX.LIST', 'META-INF/*.SF', 'META-INF/*.DSA', 'META-INF/*.RSA')

        PublishArtifact shadowArtifact = project.artifacts.add('shadow', shadow)
        project.components.add(new ShadowJavaLibrary(shadowArtifact, project.configurations.shadow.allDependencies))
    }

    class ShadowJavaLibrary extends JavaLibrary {

        ShadowJavaLibrary(PublishArtifact jarArtifact, DependencySet runtimeDependencies) {
            super(jarArtifact, runtimeDependencies)
        }

        @Override
        String getName() {
            return 'shadow'
        }
    }
}
