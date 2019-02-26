package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.KnowsTask
import groovy.transform.CompileStatic
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.util.GradleVersion

class ShadowBasePlugin implements Plugin<Project> {

    static final String EXTENSION_NAME = 'shadow'
    static final String SHADOW_CONFIGURATION_NAME = 'shadow'
    static final String SHADOW_OUTGOING_API_CONFIGURATION_NAME = 'shadowApiElements'
    static final String SHADOW_OUTGOING_RUNTIME_CONFIGURATION_NAME = 'shadowRuntimeElements'

    @Override
    void apply(Project project) {
        if (GradleVersion.current() < GradleVersion.version("5.0")) {
            throw new GradleException("This version of Shadow supports Gradle 5.0+ only. Please upgrade.")
        }
        project.extensions.create(EXTENSION_NAME, ShadowExtension, project)
        createShadowConfigurations(project)

        KnowsTask knows = project.tasks.create(KnowsTask.NAME, KnowsTask)
        knows.group = ShadowJavaPlugin.SHADOW_GROUP
        knows.description = KnowsTask.DESC
    }

    @CompileStatic
    private void createShadowConfigurations(Project project) {
        project.configurations.with {
            def shadowConf = create(SHADOW_CONFIGURATION_NAME)
            [(SHADOW_OUTGOING_API_CONFIGURATION_NAME): Usage.JAVA_API,
             (SHADOW_OUTGOING_RUNTIME_CONFIGURATION_NAME): Usage.JAVA_RUNTIME
            ].each { configurationName, usage ->
                create(configurationName) { Configuration conf ->
                    conf.with {
                        extendsFrom shadowConf
                        canBeResolved = false
                        canBeConsumed = true
                        attributes {
                            it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, usage))
                            it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements, LibraryElements.JAR))
                            it.attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling, Bundling.SHADOWED))
                            it.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, Integer.valueOf(JavaVersion.current().majorVersion))
                        }
                    }
                }
            }
        }
    }
}
