package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.KnowsTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.GradleVersion

class ShadowBasePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    if (GradleVersion.current() < GradleVersion.version("8.3")) {
      throw GradleException("This version of Shadow supports Gradle 8.3+ only. Please upgrade.")
    }

    project.extensions.create(EXTENSION_NAME, ShadowExtension::class.java, project)
    project.configurations.create(CONFIGURATION_NAME)

    project.tasks.register(KnowsTask.NAME, KnowsTask::class.java) {
      it.group = ShadowJavaPlugin.SHADOW_GROUP
      it.description = KnowsTask.DESC
    }
  }

  companion object {
    const val EXTENSION_NAME: String = "shadow"
    const val CONFIGURATION_NAME: String = "shadow"
    const val COMPONENT_NAME: String = "shadow"
  }
}
