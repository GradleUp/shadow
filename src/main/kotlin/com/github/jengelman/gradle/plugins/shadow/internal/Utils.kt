package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.InputStream

internal object Utils {

  fun requireResourceAsText(name: String): String {
    return requireResourceAsStream(name).bufferedReader().readText()
  }

  private fun requireResourceAsStream(name: String): InputStream {
    return this::class.java.getResourceAsStream(name) ?: error("Resource $name not found.")
  }
}
