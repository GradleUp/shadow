package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.KnowsTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.GradleVersion

abstract class ShadowBasePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        if (GradleVersion.current() < GradleVersion.version("8.3")) {
            throw GradleException("This version of Shadow supports Gradle 8.3+ only. Please upgrade.")
        }
        project.extensions.create(EXTENSION_NAME, ShadowExtension::class.java, project)
        project.configurations.create(CONFIGURATION_NAME)
        project.tasks.register(KnowsTask.NAME, KnowsTask::class.java) { knows ->
            knows.group = GROUP_NAME
            knows.description = KnowsTask.DESC
        }
    }

    companion object {
        const val SHADOW: String = "shadow"
        const val GROUP_NAME: String = SHADOW
        const val EXTENSION_NAME: String = SHADOW
        const val CONFIGURATION_NAME: String = SHADOW
        const val COMPONENT_NAME: String = SHADOW
        const val DISTRIBUTION_NAME: String = SHADOW
    }
}
