package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin

import javax.inject.Inject

class ShadowJavaPlugin implements Plugin<Project> {

    public static final String SHADOW_JAR_TASK_NAME = 'shadowJar'
    public static final String SHADOW_GROUP = 'Shadow'

    private final SoftwareComponentFactory softwareComponentFactory

    @Inject
    ShadowJavaPlugin(SoftwareComponentFactory softwareComponentFactory) {
        this.softwareComponentFactory = softwareComponentFactory
    }

    @Override
    void apply(Project project) {
        def shadowTaskProvider = configureShadowTask(project)

        project.configurations.compileClasspath.extendsFrom project.configurations.shadow

        project.configurations.register("shadowRuntimeElements") {
            it.extendsFrom(project.configurations.getByName(ShadowBasePlugin.CONFIGURATION_NAME))
            it.canBeConsumed = true
            it.canBeResolved = false
            it.attributes {
                it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                it.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.LIBRARY))
                it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
                it.attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.SHADOWED))
            }
            it.outgoing.artifact(shadowTaskProvider)
        }

        project.components.named("java", AdhocComponentWithVariants) {
            it.addVariantsFromConfiguration(project.configurations.shadowRuntimeElements) {
                it.mapToOptional()
            }
        }

        AdhocComponentWithVariants shadow = softwareComponentFactory.adhoc("shadow")
        project.components.add(shadow)
        shadow.addVariantsFromConfiguration(project.configurations.shadowRuntimeElements) {
            it.mapToMavenScope("runtime")
        }

        project.plugins.withType(JavaGradlePluginPlugin).configureEach {
            // Remove the gradleApi so it isn't merged into the jar file.
            // This is required because 'java-gradle-plugin' adds gradleApi() to the 'api' configuration.
            // See https://github.com/gradle/gradle/blob/972c3e5c6ef990dd2190769c1ce31998a9402a79/subprojects/plugin-development/src/main/java/org/gradle/plugin/devel/plugins/JavaGradlePluginPlugin.java#L161
            project.configurations.named(JavaPlugin.API_CONFIGURATION_NAME) {
                it.dependencies.remove(project.dependencies.gradleApi())
            }
            // Compile only gradleApi() to make sure the plugin can compile against Gradle API.
            project.configurations.named(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME) {
                it.dependencies.add(project.dependencies.gradleApi())
            }
        }
    }

    protected static TaskProvider<ShadowJar> configureShadowTask(Project project) {
        SourceSetContainer sourceSets = project.extensions.getByType(SourceSetContainer)
        def taskProvider = project.tasks.register(SHADOW_JAR_TASK_NAME, ShadowJar) { shadow ->
            shadow.group = SHADOW_GROUP
            shadow.description = 'Create a combined JAR of project and runtime dependencies'
            shadow.archiveClassifier.set("all")
            shadow.manifest.inheritFrom(project.tasks.jar.manifest)
            def libsProvider = project.provider { ->
                [
                        (project.tasks.getByName('jar') as Jar).manifest.attributes.get('Class-Path')
                ]
            }
            def files = project.objects.fileCollection()
                    .from(project.configurations.named(ShadowBasePlugin.CONFIGURATION_NAME))
            shadow.doFirst {
                if (!files.empty) {
                    def libs = libsProvider.get()
                    libs.addAll files.collect { "${it.name}" }
                    shadow.manifest.attributes 'Class-Path': libs.findAll { it }.join(' ')
                }
            }
            shadow.from(sourceSets.main.output)
            shadow.configurations = [
                    project.configurations.findByName('runtimeClasspath') ?: project.configurations.runtime
            ]
            shadow.exclude('META-INF/INDEX.LIST', 'META-INF/*.SF', 'META-INF/*.DSA', 'META-INF/*.RSA', 'module-info.class')
        }
        project.artifacts.add(ShadowBasePlugin.CONFIGURATION_NAME, taskProvider)
        return taskProvider
    }
}
