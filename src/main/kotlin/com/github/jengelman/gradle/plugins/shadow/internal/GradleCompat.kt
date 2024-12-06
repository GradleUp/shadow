package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
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
internal fun ConfigurableFileCollection.conventionCompat(vararg paths: Any): ConfigurableFileCollection {
  return if (GradleVersion.current() >= GradleVersion.version("8.8")) {
    convention(paths)
  } else {
    setFrom(paths)
    this
  }
}
