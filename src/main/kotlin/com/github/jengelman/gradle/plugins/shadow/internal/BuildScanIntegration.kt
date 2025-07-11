package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import org.gradle.api.Project

public fun Project.addBuildScanCustomValues() {
  extensions.configure<DevelocityConfiguration>("develocity") {
    val buildScan = it.buildScan
    val shadowTasks = tasks.withType(ShadowJar::class.java)
    shadowTasks.configureEach { task ->
      buildScan.buildFinished {
        // TODO: add actual Shadow stats as custom values.
        buildScan.value("shadow.${task.path}.executed", "true")
      }
    }
  }
}
