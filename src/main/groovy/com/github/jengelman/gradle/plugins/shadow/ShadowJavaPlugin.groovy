package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer
import org.gradle.api.artifacts.maven.MavenPom
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.Upload
import org.gradle.configuration.project.ProjectConfigurationActionContainer
import org.gradle.util.GradleVersion

import javax.inject.Inject

class ShadowJavaPlugin implements Plugin<Project> {

    static final String SHADOW_JAR_TASK_NAME = 'shadowJar'
    static final String SHADOW_UPLOAD_TASK = 'uploadShadow'

    private final ProjectConfigurationActionContainer configurationActionContainer;

    @Inject
    ShadowJavaPlugin(ProjectConfigurationActionContainer configurationActionContainer) {
        this.configurationActionContainer = configurationActionContainer
    }

    @Override
    void apply(Project project) {
        configureShadowTask(project)

        project.configurations.compileClasspath.extendsFrom project.configurations.shadow

        project.configurations {
            shadowRuntimeElements {
                canBeConsumed = true
                canBeResolved = false
                attributes {
                    it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                    it.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.LIBRARY))
                    it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
                    it.attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.SHADOWED))
                }
                outgoing.artifact(project.tasks.named(SHADOW_JAR_TASK_NAME))
            }
        }

        project.configurations.shadowRuntimeElements.extendsFrom project.configurations.shadow

        project.components.java {
            addVariantsFromConfiguration(project.configurations.shadowRuntimeElements) {
                mapToOptional() // make it a Maven optional dependency
            }
        }
    }

    protected void configureShadowTask(Project project) {
        JavaPluginConvention convention = project.convention.getPlugin(JavaPluginConvention)
        project.tasks.register(SHADOW_JAR_TASK_NAME, ShadowJar) { shadow ->
            shadow.group = 'distribution'
            shadow.description = 'Create a JAR combining project and runtime dependencies'
            if (GradleVersion.current() >= GradleVersion.version("5.1")) {
                shadow.archiveClassifier.set("all")
            } else {
                shadow.conventionMapping.with {
                    map('classifier') {
                        'all'
                    }
                }
            }
            shadow.manifest.inheritFrom project.tasks.jar.manifest
            def libsProvider = project.provider { -> [project.tasks.jar.manifest.attributes.get('Class-Path')] }
            def files = project.objects.fileCollection().from { ->
                project.configurations.findByName(ShadowBasePlugin.CONFIGURATION_NAME)
            }
            shadow.doFirst {
                if (!files.empty) {
                    def libs = libsProvider.get()
                    libs.addAll files.collect { "${it.name}" }
                    manifest.attributes 'Class-Path': libs.findAll { it }.join(' ')
                }
            }
            shadow.from(convention.sourceSets.main.output)
            shadow.configurations = [project.configurations.findByName('runtimeClasspath') ?
                                             project.configurations.runtimeClasspath : project.configurations.runtime]
            shadow.exclude('META-INF/INDEX.LIST', 'META-INF/*.SF', 'META-INF/*.DSA', 'META-INF/*.RSA', 'module-info.class')
        }
        project.artifacts.add(ShadowBasePlugin.CONFIGURATION_NAME, project.tasks.named(SHADOW_JAR_TASK_NAME))
        configureShadowUpload()
    }

    private void configureShadowUpload() {
        configurationActionContainer.add(new Action<Project>() {
            void execute(Project project) {
                project.plugins.withType(MavenPlugin) {
                    project.tasks.withType(Upload).configureEach { upload ->
                        if (upload.name != SHADOW_UPLOAD_TASK) {
                            return
                        }
                        upload.configuration = project.configurations.shadow
                        MavenPom pom = upload.repositories.mavenDeployer.pom
                        pom.scopeMappings.mappings.remove(project.configurations.compile)
                        pom.scopeMappings.mappings.remove(project.configurations.runtime)
                        pom.scopeMappings.addMapping(MavenPlugin.RUNTIME_PRIORITY, project.configurations.shadow, Conf2ScopeMappingContainer.RUNTIME)
                    }
                }
            }
        })
    }
}
