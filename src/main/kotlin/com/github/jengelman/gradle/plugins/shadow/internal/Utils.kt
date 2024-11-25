package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.InputStream

@Suppress("NOTHING_TO_INLINE")
internal inline fun <T> unsafeLazy(noinline initializer: () -> T): Lazy<T> =
  lazy(LazyThreadSafetyMode.NONE, initializer)

internal fun Class<*>.requireResourceAsText(name: String): String {
  return requireResourceAsStream(name).bufferedReader().readText()
}

private fun Class<*>.requireResourceAsStream(name: String): InputStream {
  return getResourceAsStream(name) ?: error("Resource $name not found.")
}
