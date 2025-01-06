package com.github.jengelman.gradle.plugins.shadow.util

import kotlinx.serialization.Serializable

@Serializable
data class GradleModuleMetadata(
  val variants: List<Variant>,
) {
  @Serializable
  data class Variant(
    val name: String,
    val attributes: Map<String, String>,
    val dependencies: List<Dependency> = emptyList(),
  ) {
    @Serializable
    data class Dependency(
      val module: String,
    )
  }
}
