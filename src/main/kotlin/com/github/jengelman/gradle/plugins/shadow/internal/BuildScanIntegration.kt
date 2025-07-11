package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import org.gradle.api.Project

public fun Project.addBuildScanCustomValues() {
  val develocity = extensions.findByType(DevelocityConfiguration::class.java)
    ?: return
  val buildScan = develocity.buildScan
  val shadowTasks = tasks.withType(ShadowJar::class.java)
  shadowTasks.configureEach { task ->
    buildScan.buildFinished {
      buildScan.value("shadow.${task.path}.executed", "true")
    }
  }
}
