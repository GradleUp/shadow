package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.KnowsTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.GradleVersion

class ShadowBasePlugin implements Plugin<Project> {

    static final String EXTENSION_NAME = 'shadow'
    static final String CONFIGURATION_NAME = 'shadow'

    @Override
    void apply(Project project) {
        if (GradleVersion.current() < GradleVersion.version("3.0")) {
            throw new GradleException("This version of Shadow supports Gradle 3.0+ only. Please upgrade.")
        }
        project.extensions.create(EXTENSION_NAME, ShadowExtension, project)
        createShadowConfiguration(project)

        KnowsTask knows = project.tasks.create(KnowsTask.NAME, KnowsTask)
        knows.group = ShadowJavaPlugin.SHADOW_GROUP
        knows.description = KnowsTask.DESC
    }

    private void createShadowConfiguration(Project project) {
        project.configurations.create(CONFIGURATION_NAME)
    }
}
