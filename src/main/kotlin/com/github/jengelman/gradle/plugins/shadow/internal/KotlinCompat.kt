package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

internal const val KOTLIN_MULTIPLATFORM_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform"

internal fun Project.isAtLeastKgp(
  version: String,
  id: String = KOTLIN_MULTIPLATFORM_PLUGIN_ID,
): Boolean {
  val actual = (plugins.getPlugin(id) as KotlinBasePlugin).pluginVersion
  return KotlinToolingVersion(actual) >= KotlinToolingVersion(version)
}
