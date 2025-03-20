package com.github.jengelman.gradle.plugins.shadow

import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.util.GradleVersion

public abstract class ShadowBasePlugin : Plugin<Project> {

  override fun apply(project: Project) {
    if (GradleVersion.current() < GradleVersion.version("8.3")) {
      throw GradleException("This version of Shadow supports Gradle 8.3+ only. Please upgrade.")
    }
    @Suppress("DEPRECATION")
    project.extensions.create(EXTENSION_NAME, ShadowExtension::class.java, project)
    project.configurations.create(CONFIGURATION_NAME)
  }

  public companion object {
    public const val SHADOW: String = "shadow"
    public const val EXTENSION_NAME: String = SHADOW
    public const val CONFIGURATION_NAME: String = SHADOW
    public const val COMPONENT_NAME: String = SHADOW
    public const val DISTRIBUTION_NAME: String = SHADOW

    public inline val ConfigurationContainer.shadow: NamedDomainObjectProvider<Configuration>
      get() = named(CONFIGURATION_NAME)
  }
}
