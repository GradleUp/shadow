package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Property
import org.gradle.util.GradleVersion

/**
 * Return `runtimeClasspath` or `runtime` configuration.
 */
internal inline val Project.runtimeConfiguration: Configuration
  get() {
    return configurations.findByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
      ?: configurations.getByName("runtime")
  }

internal inline fun <reified T : Any> ObjectFactory.property(defaultValue: T? = null): Property<T> {
  return property(T::class.java).apply {
    if (defaultValue != null) convention(defaultValue)
  }
}

/**
 * TODO: this could be removed after bumping the min Gradle requirement to 8.8 or above.
 */
internal inline fun ObjectFactory.fileCollection(path: () -> Any): ConfigurableFileCollection {
  return fileCollection().apply {
    @Suppress("UnstableApiUsage")
    if (GradleVersion.current() >= GradleVersion.version("8.8")) {
      convention(path())
    } else {
      setFrom(path())
    }
  }
}

/**
 * TODO: this could be removed after bumping the min Gradle requirement to 8.11 or above.
 */
internal fun ProjectDependency.dependencyProjectCompat(project: Project): Project {
  return if (GradleVersion.current() >= GradleVersion.version("8.11")) {
    project.project(path)
  } else {
    @Suppress("DEPRECATION")
    dependencyProject
  }
}
