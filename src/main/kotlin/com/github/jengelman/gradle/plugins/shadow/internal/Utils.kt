package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.InputStream
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin

/**
 * Return `runtimeClasspath` or `runtime` configuration.
 */
internal inline val Project.runtimeConfiguration: Configuration get() {
  return configurations.findByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
    ?: configurations.getByName("runtime")
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun <T> unsafeLazy(noinline initializer: () -> T): Lazy<T> =
  lazy(LazyThreadSafetyMode.NONE, initializer)

internal fun Class<*>.requireResourceAsText(name: String): String {
  return requireResourceAsStream(name).bufferedReader().readText()
}

private fun Class<*>.requireResourceAsStream(name: String): InputStream {
  return getResourceAsStream(name) ?: error("Resource $name not found.")
}
