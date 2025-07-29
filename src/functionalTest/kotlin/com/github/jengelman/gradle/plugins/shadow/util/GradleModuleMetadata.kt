package com.github.jengelman.gradle.plugins.shadow.util

import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME

/**
 * Refs the format from [Gradle Module Metadata](https://github.com/gradle/gradle/blob/master/platforms/documentation/docs/src/docs/design/gradle-module-metadata-latest-specification.md).
 */
data class GradleModuleMetadata(
  private val variants: List<Variant>,
) {
  val apiElementsVariant: Variant get() = variants.single { it.name == API_ELEMENTS_CONFIGURATION_NAME }
  val runtimeElementsVariant: Variant get() = variants.single { it.name == RUNTIME_ELEMENTS_CONFIGURATION_NAME }
  val shadowRuntimeElementsVariant: Variant get() = variants.single { it.name == SHADOW_RUNTIME_ELEMENTS_CONFIGURATION_NAME }

  val variantNames: List<String> get() = variants.map { it.name }

  data class Variant(
    val name: String,
    val attributes: Map<String, String>,
    private val dependencies: List<Dependency> = emptyList(),
    private val files: List<File> = emptyList(),
  ) {
    val coordinates: List<String> get() = dependencies.map { it.coordinate }
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
