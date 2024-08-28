package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.configuration.project.ProjectConfigurationActionContainer
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin

import javax.inject.Inject

class ShadowJavaPlugin implements Plugin<Project> {

    public static final String SHADOW_JAR_TASK_NAME = 'shadowJar'
    public static final String SHADOW_GROUP = 'Shadow'

    private final ProjectConfigurationActionContainer configurationActionContainer

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

        // Remove the gradleApi so it isn't merged into the jar file.
        // This is required because 'java-gradle-plugin' adds gradleApi() to the 'api' configuration.
        // See https://github.com/gradle/gradle/blob/972c3e5c6ef990dd2190769c1ce31998a9402a79/subprojects/plugin-development/src/main/java/org/gradle/plugin/devel/plugins/JavaGradlePluginPlugin.java#L161
        project.plugins.withType(JavaGradlePluginPlugin).configureEach {
            // needed to prevent inclusion of gradle-api into shadow JAR
            project.configurations.named(JavaPlugin.API_CONFIGURATION_NAME) {
                it.dependencies.remove(project.dependencies.gradleApi())
            }
        }
    }

    protected static void configureShadowTask(Project project) {
        SourceSetContainer sourceSets = project.extensions.getByType(SourceSetContainer)
        project.tasks.register(SHADOW_JAR_TASK_NAME, ShadowJar) { shadow ->
            shadow.group = SHADOW_GROUP
            shadow.description = 'Create a combined JAR of project and runtime dependencies'
            shadow.archiveClassifier.set("all")
            shadow.manifest.inheritFrom(project.tasks.jar.manifest)
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
            shadow.from(sourceSets.main.output)
            shadow.configurations = [project.configurations.findByName('runtimeClasspath') ?
                                             project.configurations.runtimeClasspath : project.configurations.runtime]
            shadow.exclude('META-INF/INDEX.LIST', 'META-INF/*.SF', 'META-INF/*.DSA', 'META-INF/*.RSA', 'module-info.class')
        }
        project.artifacts.add(ShadowBasePlugin.CONFIGURATION_NAME, project.tasks.named(SHADOW_JAR_TASK_NAME))
    }
}
