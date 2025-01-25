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
    val files: List<File> = emptyList(),
  ) {
    val depStrings: List<String> get() = dependencies.map { it.coordinate }
    val fileNames: List<String> get() = files.map { it.name }

    data class Dependency(
      val group: String,
      val module: String,
      val version: Version,
    ) {
      val coordinate: String get() = "$group:$module:${version.requires}"

      data class Version(val requires: String)
    }

    data class File(
      val name: String,
    )
  }
}
