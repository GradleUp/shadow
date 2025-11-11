package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin

internal const val KOTLIN_MULTIPLATFORM_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform"

internal fun Project.isAtLeastKgp(
  version: String,
  id: String = KOTLIN_MULTIPLATFORM_PLUGIN_ID,
): Boolean {
  val (major, minor, patch) = version.normalizeVersion()
  val (actualMajor, actualMinor, actualPatch) = (plugins.getPlugin(id) as KotlinBasePlugin).pluginVersion.normalizeVersion()
  return KotlinVersion(actualMajor, actualMinor, actualPatch) >= KotlinVersion(major, minor, patch)
}

private fun String.normalizeVersion(): Triple<Int, Int, Int> {
  val (major, minor, patch) = takeWhile { it != '-' }.split(".").map { it.toInt() }
  return Triple(major, minor, patch)
}
