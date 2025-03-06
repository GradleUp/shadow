package com.github.jengelman.gradle.plugins.shadow.snippet

enum class DslLang {
//  Kotlin,
  Groovy,
  ;

  override fun toString(): String = name.lowercase()
}
