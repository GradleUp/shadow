package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.legacy.LegacyShadowPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaPlugin

public class ShadowPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    with(project.plugins) {
      apply(ShadowBasePlugin::class.java)
      apply(LegacyShadowPlugin::class.java)
      withType(JavaPlugin::class.java) {
        apply(ShadowJavaPlugin::class.java)
      }
      withType(ApplicationPlugin::class.java) {
        apply(ShadowApplicationPlugin::class.java)
      }
    }
  }
}
