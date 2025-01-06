package com.github.jengelman.gradle.plugins.shadow.util

data class GradleModuleMetadata(
  val variants: List<Variant>,
) {
  data class Variant(
    val name: String,
    val attributes: Map<String, String>,
    val dependencies: List<Dependency> = emptyList(),
  ) {
    data class Dependency(
      val module: String,
    )
  }
}
