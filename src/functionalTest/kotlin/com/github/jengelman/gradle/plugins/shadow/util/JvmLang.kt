package com.github.jengelman.gradle.plugins.shadow.util

enum class JvmLang {
//  Groovy,
  Java,
  Kotlin,
//  Scala,
  ;

  override fun toString(): String = name.lowercase()
}
