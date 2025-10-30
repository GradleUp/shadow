package com.github.jengelman.gradle.plugins.shadow.util

enum class JvmLang(
  val suffix: String,
) {
  Java("java"),
  Kotlin("kt"),
  ;

  override fun toString(): String = name.lowercase()
}
