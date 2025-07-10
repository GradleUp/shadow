package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.internal.KOTLIN_MULTIPLATFORM_PLUGIN_ID
import com.github.jengelman.gradle.plugins.shadow.legacy.LegacyShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.gradle.develocity.agent.gradle.scan.BuildScanConfiguration
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaPlugin

public abstract class ShadowPlugin : Plugin<Project> {

  override fun apply(project: Project): Unit = with(project.plugins) {
    apply(ShadowBasePlugin::class.java)
    @Suppress("WithTypeWithoutConfigureEach")
    withType(JavaPlugin::class.java) {
      apply(ShadowJavaPlugin::class.java)
    }
    @Suppress("WithTypeWithoutConfigureEach")
    withType(ApplicationPlugin::class.java) {
      apply(ShadowApplicationPlugin::class.java)
    }
    withId(KOTLIN_MULTIPLATFORM_PLUGIN_ID) {
      apply(ShadowKmpPlugin::class.java)
    }

    // Apply the legacy plugin last.
    // Because we apply the ShadowJavaPlugin/ShadowApplication plugin in a withType callback for the
    // respective JavaPlugin/ApplicationPlugin, it may still apply before the shadowJar task is created etc.
    // If the user applies shadow before those plugins. However, this is fine, because this was also
    // the behavior with the old plugin when applying in that order.
    apply(LegacyShadowPlugin::class.java)

    project.plugins.withId("com.gradle.develocity") {
      configureBuildScan(project)
    }
  }

  private fun configureBuildScan(project: Project) {
    val shadowTasks = project.tasks.withType(ShadowJar::class.java)
    val buildScan = project.extensions.getByType(BuildScanConfiguration::class.java)
    buildScan.buildFinished {
      shadowTasks.forEach { task ->
        // TODO Add actual Shadow stats as custom values
        buildScan.value("shadow.${task.path}.executed", "true")
      }
    }
  }
}
