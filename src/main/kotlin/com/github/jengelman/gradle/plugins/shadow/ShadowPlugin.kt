package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.internal.KOTLIN_MULTIPLATFORM_PLUGIN_ID
import com.github.jengelman.gradle.plugins.shadow.internal.addBuildScanCustomValues
import com.github.jengelman.gradle.plugins.shadow.internal.findOptionalProperty
import com.github.jengelman.gradle.plugins.shadow.legacy.LegacyShadowPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.cc.base.logger

public abstract class ShadowPlugin : Plugin<Project> {

  override fun apply(project: Project): Unit = with(project.plugins) {
    apply(ShadowBasePlugin::class.java)
    // org.gradle.api.plugins.JavaPlugin
    withId("org.gradle.java") {
      apply(ShadowJavaPlugin::class.java)
    }
    // org.gradle.api.plugins.ApplicationPlugin
    withId("org.gradle.application") {
      apply(ShadowApplicationPlugin::class.java)
    }
    withId(KOTLIN_MULTIPLATFORM_PLUGIN_ID) {
      apply(ShadowKmpPlugin::class.java)
    }
    withId("com.android.base") {
      error(
        "Shadow does not support using with AGP, you may need Android Fused Library plugin instead. " +
          "See https://developer.android.com/build/publish-library/fused-library",
      )
    }
    project.configureBuildScan()

    // Apply the legacy plugin last.
    // Because we apply the ShadowJavaPlugin/ShadowApplication plugin in a withType callback for the
    // respective JavaPlugin/ApplicationPlugin, it may still apply before the shadowJar task is created etc.
    // If the user applies shadow before those plugins. However, this is fine, because this was also
    // the behavior with the old plugin when applying in that order.
    apply(LegacyShadowPlugin::class.java)

    project.afterEvaluate {
      project.tasks.findByName("assemble")?.let {
        logger.info("Calling findByName should be allowed in afterEvaluate.")
      }
    }
  }

  private fun Project.configureBuildScan() {
    val enableDevelocityIntegration = findOptionalProperty(ENABLE_DEVELOCITY_INTEGRATION_PROPERTY)?.toBoolean() ?: false
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
