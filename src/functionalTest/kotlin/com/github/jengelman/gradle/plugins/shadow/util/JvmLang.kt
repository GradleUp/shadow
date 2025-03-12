package com.github.jengelman.gradle.plugins.shadow.util

enum class JvmLang {
  Groovy,
  Java,
  Kotlin,
  ;

  override fun toString(): String = name.lowercase()
}
