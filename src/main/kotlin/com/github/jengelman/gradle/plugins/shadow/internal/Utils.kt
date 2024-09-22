package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.InputStream

internal fun requireResourceAsText(name: String): String {
  return requireResourceAsStream(name).bufferedReader().readText()
}

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

private fun requireResourceAsStream(name: String): InputStream {
  return Unit::class.java.getResourceAsStream(name) ?: error("Resource $name not found.")
}
