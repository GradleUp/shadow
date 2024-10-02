package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.InputStream

internal inline fun <R> runOrThrow(
  error: (e: Exception) -> Nothing = { throw RuntimeException(it) },
  block: () -> R,
): R {
  return try {
    block()
  } catch (e: Exception) {
    error(e)
  }
}

internal fun Class<*>.requireResourceAsText(name: String): String {
  return requireResourceAsStream(name).bufferedReader().readText()
}

private fun Class<*>.requireResourceAsStream(name: String): InputStream {
  return getResourceAsStream(name) ?: error("Resource $name not found.")
}
