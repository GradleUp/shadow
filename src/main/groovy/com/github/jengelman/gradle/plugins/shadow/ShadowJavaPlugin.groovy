package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer
import org.gradle.api.artifacts.maven.MavenPom
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.Upload
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.configuration.project.ProjectConfigurationActionContainer

import javax.inject.Inject

class ShadowJavaPlugin implements Plugin<Project> {

    static final String SHADOW_JAR_TASK_NAME = 'shadowJar'
    static final String SHADOW_UPLOAD_TASK = 'uploadShadow'
    static final String SHADOW_GROUP = 'Shadow'

    private final ProjectConfigurationActionContainer configurationActionContainer;

    @Inject
    ShadowJavaPlugin(ProjectConfigurationActionContainer configurationActionContainer) {
        this.configurationActionContainer = configurationActionContainer
    }

    @Override
    void apply(Project project) {
        configureShadowTask(project)

        project.configurations.compileClasspath.extendsFrom project.configurations.shadow

    }

    protected void configureShadowTask(Project project) {
        JavaPluginConvention convention = project.convention.getPlugin(JavaPluginConvention)
        ShadowJar shadow = project.tasks.create(SHADOW_JAR_TASK_NAME, ShadowJar)
        shadow.group = SHADOW_GROUP
        shadow.description = 'Create a combined JAR of project and runtime dependencies'
        shadow.conventionMapping.with {
            map('classifier') {
                'all'
            }
        }
        shadow.manifest.inheritFrom project.tasks.jar.manifest
        shadow.doFirst {
            def files = project.configurations.findByName(ShadowBasePlugin.CONFIGURATION_NAME).files
            if (files) {
                def libs = [project.tasks.jar.manifest.attributes.get('Class-Path')]
                libs.addAll files.collect { "${it.name}" }
                manifest.attributes 'Class-Path': libs.findAll { it }.join(' ')
            }
        }
        shadow.from(convention.sourceSets.main.output)
        shadow.configurations = [project.configurations.findByName('runtimeClasspath') ?
                                         project.configurations.runtimeClasspath : project.configurations.runtime]
        shadow.exclude('META-INF/INDEX.LIST', 'META-INF/*.SF', 'META-INF/*.DSA', 'META-INF/*.RSA')

        project.artifacts.add(ShadowBasePlugin.CONFIGURATION_NAME, shadow)
        configureShadowUpload()
    }

    private void configureShadowUpload() {
        configurationActionContainer.add(new Action<Project>() {
            void execute(Project project) {
                project.plugins.withType(MavenPlugin) {
                    Upload upload = project.tasks.withType(Upload).findByName(SHADOW_UPLOAD_TASK)
                    if (!upload) {
                        return
                    }
                    upload.configuration = project.configurations.shadow
                    MavenPom pom = upload.repositories.mavenDeployer.pom
                    pom.scopeMappings.mappings.remove(project.configurations.compile)
                    pom.scopeMappings.mappings.remove(project.configurations.runtime)
                    pom.scopeMappings.addMapping(MavenPlugin.RUNTIME_PRIORITY, project.configurations.shadow, Conf2ScopeMappingContainer.RUNTIME)
                }
            }
        })
    }
}
