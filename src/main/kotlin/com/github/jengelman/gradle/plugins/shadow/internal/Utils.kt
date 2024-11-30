package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.File
import java.io.InputStream
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.DefaultFileTreeElement
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Property
import org.gradle.internal.file.Chmod
import org.gradle.internal.file.Stat

/**
 * Return `runtimeClasspath` or `runtime` configuration.
 */
internal inline val Project.runtimeConfiguration: Configuration get() {
  return configurations.findByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
    ?: configurations.getByName("runtime")
}

internal inline fun <reified T : Any> ObjectFactory.property(defaultValue: T? = null): Property<T> {
  return property(T::class.java).apply {
    if (defaultValue != null) convention(defaultValue)
  }
}

internal fun createDefaultFileTreeElement(
  file: File? = null,
  relativePath: RelativePath,
  chmod: Chmod? = null,
  stat: Stat? = null,
): DefaultFileTreeElement {
  return DefaultFileTreeElement(file, relativePath, chmod, stat)
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun <T> unsafeLazy(noinline initializer: () -> T): Lazy<T> {
  return lazy(LazyThreadSafetyMode.NONE, initializer)
}

internal fun Class<*>.requireResourceAsText(name: String): String {
  return requireResourceAsStream(name).bufferedReader().readText()
}

private fun Class<*>.requireResourceAsStream(name: String): InputStream {
  return getResourceAsStream(name) ?: error("Resource $name not found.")
}
