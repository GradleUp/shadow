package com.github.jengelman.gradle.plugins.shadow.util

enum class JvmLang(
  val suffix: String,
) {
  Groovy("groovy"),
  Java("java"),
  Kotlin("kt"),
  Scala("scala"),
  ;

  override fun toString(): String = name.lowercase()
}
