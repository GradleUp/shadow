package com.github.jengelman.gradle.plugins.shadow

enum class DslLang {
  Kotlin,
  Groovy;

  override fun toString(): String = name.lowercase()
}
