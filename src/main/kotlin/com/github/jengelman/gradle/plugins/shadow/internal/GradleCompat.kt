package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.util.GradleVersion

/**
 * Return `runtimeClasspath` or `runtime` configuration.
 */
internal inline val Project.runtimeConfiguration: Configuration
  get() {
    return configurations.findByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
      ?: configurations.getByName("runtime")
  }

@Suppress("UNCHECKED_CAST")
internal inline fun <reified V : Any, reified P : Provider<V>> ObjectFactory.property(
  defaultValue: Any? = null,
): P {
  return when (P::class.java) {
    ListProperty::class.java -> listProperty(V::class.java).apply {
      if (defaultValue != null) convention(defaultValue as List<V>)
    }
    SetProperty::class.java -> setProperty(V::class.java).apply {
      if (defaultValue != null) convention(defaultValue as Set<V>)
    }
    MapProperty::class.java -> mapProperty(String::class.java, V::class.java).apply {
      if (defaultValue != null) convention(defaultValue as Map<String, V>)
    }
    else -> property(V::class.java).apply {
      if (defaultValue != null) convention(defaultValue as V)
    }
  } as P
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
