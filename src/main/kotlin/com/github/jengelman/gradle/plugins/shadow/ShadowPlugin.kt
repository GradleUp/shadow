package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.internal.KOTLIN_MULTIPLATFORM_PLUGIN_ID
import com.github.jengelman.gradle.plugins.shadow.internal.addBuildScanCustomValues
import com.github.jengelman.gradle.plugins.shadow.legacy.LegacyShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
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

    project.extensions.findByName("develocity")?.let {
      project.configureBuildScan()
    }
  }

  private fun Project.configureBuildScan() {
    val enableDevelocityIntegration = providers.gradleProperty(
      ENABLE_DEVELOCITY_INTEGRATION_PROPERTY,
    ).map { it.toBoolean() }.getOrElse(false)

    if (enableDevelocityIntegration) {
      logger.info("Enabling Develocity integration for Shadow plugin.")
    } else {
      logger.info("Skipping Develocity integration for Shadow plugin.")
      return
    }
    addBuildScanCustomValues()
  }

  public companion object {
    public const val ENABLE_DEVELOCITY_INTEGRATION_PROPERTY: String = "com.gradleup.shadow.enableDevelocityIntegration"
  }
}
