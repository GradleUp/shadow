package com.github.jengelman.gradle.plugins.shadow.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused") // For classpath loading in build.gradle.kts.
abstract class BuildLogicPlugin : Plugin<Project> {
  override fun apply(target: Project) = Unit
}
