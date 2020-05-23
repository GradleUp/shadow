package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer
import org.gradle.api.artifacts.maven.MavenPom
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.Upload
import org.gradle.configuration.project.ProjectConfigurationActionContainer
import org.gradle.util.GradleVersion

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

        project.configurations {
            shadowRuntimeElements {
                canBeConsumed = true
                canBeResolved = false
                attributes {
                    it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
                    it.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.LIBRARY))
                    it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
                    it.attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.EMBEDDED))
                    // means no relocation of packages
                    it.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
                }
                outgoing.artifact(project.tasks.getByName(SHADOW_JAR_TASK_NAME))
            }
        }

        project.components.java {
            addVariantsFromConfiguration(project.configurations.shadowRuntimeElements) {
                mapToOptional() // make it a Maven optional dependency
            }
        }

    }

    protected void configureShadowTask(Project project) {
        JavaPluginConvention convention = project.convention.getPlugin(JavaPluginConvention)
        ShadowJar shadow = project.tasks.create(SHADOW_JAR_TASK_NAME, ShadowJar)
        shadow.group = SHADOW_GROUP
        shadow.description = 'Create a combined JAR of project and runtime dependencies'
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
        shadow.exclude('META-INF/INDEX.LIST', 'META-INF/*.SF', 'META-INF/*.DSA', 'META-INF/*.RSA', 'module-info.class')

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
