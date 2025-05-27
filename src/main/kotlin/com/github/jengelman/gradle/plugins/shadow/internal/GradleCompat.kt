package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.util.GradleVersion

/**
 * Return `runtimeClasspath` or `runtime` configuration.
 */
internal inline val Project.runtimeConfiguration: Configuration
  get() {
    return configurations.findByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
      ?: configurations.getByName("runtime")
  }

internal inline val Project.sourceSets: SourceSetContainer
  get() = extensions.getByType(SourceSetContainer::class.java)

internal inline val Project.distributions: DistributionContainer
  get() = extensions.getByType(DistributionContainer::class.java)

internal inline val Project.applicationExtension: JavaApplication
  get() = extensions.getByType(JavaApplication::class.java)

internal inline val Project.javaPluginExtension: JavaPluginExtension
  get() = extensions.getByType(JavaPluginExtension::class.java)

internal inline val Project.javaToolchainService: JavaToolchainService
  get() = extensions.getByType(JavaToolchainService::class.java)

internal inline val TaskContainer.jar: TaskProvider<Jar>
  get() = named("jar", Jar::class.java)

internal inline fun <reified V : Any> ObjectFactory.property(
  defaultValue: Any? = null,
): Property<V> = property(V::class.java).apply {
  defaultValue ?: return@apply
  if (defaultValue is Provider<*>) {
    @Suppress("UNCHECKED_CAST")
    convention(defaultValue as Provider<V>)
  } else {
    convention(defaultValue as V)
  }
}

@Suppress("UNCHECKED_CAST")
internal inline fun <reified V : Any> ObjectFactory.setProperty(
  defaultValue: Any? = null,
): SetProperty<V> = setProperty(V::class.java).apply {
  defaultValue ?: return@apply
  if (defaultValue is Provider<*>) {
    convention(defaultValue as Provider<Iterable<V>>)
  } else {
    convention(defaultValue as Iterable<V>)
  }
}

@Suppress("UNCHECKED_CAST")
internal inline fun <reified V : Any> ObjectFactory.mapProperty(
  defaultValue: Any? = null,
): MapProperty<String, V> = mapProperty(String::class.java, V::class.java).apply {
  defaultValue ?: return@apply
  if (defaultValue is Provider<*>) {
    convention(defaultValue as Provider<Map<String, V>>)
  } else {
    convention(defaultValue as Map<String, V>)
  }
}

/**
 * TODO: this could be removed after bumping the min Gradle requirement to 8.8 or above.
 */
internal inline fun ObjectFactory.fileCollection(
  path: () -> Any,
): ConfigurableFileCollection = fileCollection().apply {
  @Suppress("UnstableApiUsage")
  if (GradleVersion.current() >= GradleVersion.version("8.8")) {
    convention(path())
  } else {
    setFrom(path())
  }
}
