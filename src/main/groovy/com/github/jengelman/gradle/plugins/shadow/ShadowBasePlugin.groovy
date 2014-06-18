package com.github.jengelman.gradle.plugins.shadow

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.initialization.ClassLoaderRegistry
import org.gradle.internal.classloader.FilteringClassLoader

import javax.inject.Inject

class ShadowBasePlugin implements Plugin<Project> {

    static final String EXTENSION_NAME = 'shadow'
    static final String CONFIGURATION_NAME = 'shadow'

    private FilteringClassLoader filteringClassLoader

    @Inject
    ShadowBasePlugin(ClassLoaderRegistry registry) {
        ClassLoader classLoader = registry.gradleApiClassLoader
        while (!filteringClassLoader) {
            if (!classLoader) {
                throw new GradleException("Could not find FilteringClassLoader!")
            }
            if (classLoader instanceof FilteringClassLoader) {
                filteringClassLoader = classLoader
            } else {
                classLoader = classLoader.parent
            }
        }
    }

    @Override
    void apply(Project project) {
        filteringClassLoader.allowPackage('org.apache.tools.zip')
        project.extensions.create(EXTENSION_NAME, ShadowExtension, project)
        createShadowConfiguration(project)
    }

    private void createShadowConfiguration(Project project) {
        project.configurations.create(CONFIGURATION_NAME)
    }
}
