package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.legacy.LegacyShadowPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaPlugin

public abstract class ShadowPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    with(project.plugins) {
      apply(ShadowBasePlugin::class.java)
      @Suppress("WithTypeWithoutConfigureEach")
      withType(JavaPlugin::class.java) {
        apply(ShadowJavaPlugin::class.java)
      }
      @Suppress("WithTypeWithoutConfigureEach")
      withType(ApplicationPlugin::class.java) {
        apply(ShadowApplicationPlugin::class.java)
      }
      withId("org.jetbrains.kotlin.multiplatform") {
        apply(ShadowJavaPlugin::class.java)
      }

      // Apply the legacy plugin last.
      // Because we apply the ShadowJavaPlugin/ShadowApplication plugin in a withType callback for the
      // respective JavaPlugin/ApplicationPlugin, it may still apply before the shadowJar task is created etc.
      // If the user applies shadow before those plugins. However, this is fine, because this was also
      // the behavior with the old plugin when applying in that order.
      apply(LegacyShadowPlugin::class.java)
    }
  }
}
