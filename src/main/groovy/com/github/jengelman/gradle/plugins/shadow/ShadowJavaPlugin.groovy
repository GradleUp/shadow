package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer
import org.gradle.api.artifacts.maven.MavenPom
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.component.ConfigurationVariantDetails
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.Upload
import org.gradle.configuration.project.ProjectConfigurationActionContainer
import org.gradle.util.GradleVersion

import javax.inject.Inject

import static org.gradle.api.plugins.JavaBasePlugin.UNPUBLISHABLE_VARIANT_ARTIFACTS

class ShadowJavaPlugin implements Plugin<Project> {

    static final String SHADOW_JAR_TASK_NAME = 'shadowJar'
    static final String SHADOW_UPLOAD_TASK = 'uploadShadow'
    static final String SHADOW_GROUP = 'Shadow'

    private final ProjectConfigurationActionContainer configurationActionContainer
    private final SoftwareComponentFactory softwareComponentFactory

    @Inject
    ShadowJavaPlugin(ProjectConfigurationActionContainer configurationActionContainer, SoftwareComponentFactory factory) {
        this.configurationActionContainer = configurationActionContainer
        this.softwareComponentFactory = factory
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
        if (GradleVersion.current() >= GradleVersion.version("5.1")) {
            shadow.archiveClassifier.set("all")
        }
        shadow.manifest.inheritFrom project.tasks.jar.manifest
        shadow.doFirst {
            def files = project.configurations.findByName(ShadowBasePlugin.SHADOW_CONFIGURATION_NAME).files
            if (files) {
                def libs = [project.tasks.jar.manifest.attributes.get('Class-Path')]
                libs.addAll files.collect { "${it.name}" }
                manifest.attributes 'Class-Path': libs.findAll { it }.join(' ')
            }
        }
        shadow.from(convention.sourceSets.main.output)
        shadow.configurations = [project.configurations.findByName('runtimeClasspath') ?
                                         project.configurations.runtimeClasspath : project.configurations.runtime]
        shadow.exclude('META-INF/INDEX.LIST', 'META-INF/*.SF', 'META-INF/*.DSA', 'META-INF/*.RSA', 'module-info.class')

        project.artifacts.add(ShadowBasePlugin.SHADOW_CONFIGURATION_NAME, shadow)
        configureShadowUpload()
        configurePublications(project, shadow)
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

    @CompileStatic
    private void configurePublications(Project project, ShadowJar shadowJar) {
        AdhocComponentWithVariants shadowComponent = softwareComponentFactory.adhoc("shadowJava")
        shadowComponent.with {
            project.components.add(it)
            // add the regular variants, with an additional "shadow" attribute
            addVariantsFromConfiguration(project.configurations.getByName(JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME), new PublishableVariantSpec("compile", false))
            addVariantsFromConfiguration(project.configurations.getByName(JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME), new PublishableVariantSpec("runtime", false))
            // now add the shadow specific variants
            addVariantsFromConfiguration(project.configurations.getByName(ShadowBasePlugin.SHADOW_OUTGOING_API_CONFIGURATION_NAME), new PublishableVariantSpec("compile", true))
            addVariantsFromConfiguration(project.configurations.getByName(ShadowBasePlugin.SHADOW_OUTGOING_RUNTIME_CONFIGURATION_NAME), new PublishableVariantSpec("runtime", true));
        }
        registerShadowArtifact(project, ShadowBasePlugin.SHADOW_OUTGOING_API_CONFIGURATION_NAME, shadowJar)
        registerShadowArtifact(project, ShadowBasePlugin.SHADOW_OUTGOING_RUNTIME_CONFIGURATION_NAME, shadowJar)
    }

    @CompileStatic
    private static void registerShadowArtifact(Project project, String configurationName, ShadowJar shadowJar) {
        project.artifacts.add(configurationName, shadowJar)
    }

    @CompileStatic
    private static class PublishableVariantSpec implements Action<ConfigurationVariantDetails> {
        private final String scope
        private final boolean optional
        PublishableVariantSpec(String scope, boolean optional) {
            this.scope = scope
            this.optional = optional
        }

        @Override
        void execute(ConfigurationVariantDetails details) {
            def variant = details.configurationVariant
            for (PublishArtifact artifact : variant.artifacts) {
                if (UNPUBLISHABLE_VARIANT_ARTIFACTS.contains(artifact.type)) {
                    details.skip()
                    return
                }
            }
            details.mapToMavenScope(scope)
            if (optional) {
                details.mapToOptional()
            }
        }
    }
}
