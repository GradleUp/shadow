package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin

internal const val KOTLIN_MULTIPLATFORM_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform"

internal fun Project.isAtLeastKgpVersion(
  major: Int,
  minor: Int,
  patch: Int,
  id: String = KOTLIN_MULTIPLATFORM_PLUGIN_ID,
): Boolean {
  val plugin = plugins.getPlugin(id) as KotlinBasePlugin
  val elements = plugin.pluginVersion.takeWhile { it != '-' }.split(".").map { it.toInt() }
  val (kgpMajor, kgpMinor, kgpPatch) = elements
  return kgpMajor >= major && kgpMinor >= minor && kgpPatch >= patch
}
