package com.github.jengelman.gradle.plugins.shadow.util

/**
 * Refs the format from [Gradle Module Metadata](https://github.com/gradle/gradle/blob/master/platforms/documentation/docs/src/docs/design/gradle-module-metadata-latest-specification.md).
 */
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
