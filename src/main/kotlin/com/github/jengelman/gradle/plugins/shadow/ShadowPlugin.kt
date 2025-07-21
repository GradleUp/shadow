package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.internal.KOTLIN_MULTIPLATFORM_PLUGIN_ID
import com.github.jengelman.gradle.plugins.shadow.legacy.LegacyShadowPlugin
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
    withId("com.android.base") {
      error(
        "Shadow does not support using with AGP, you may need Android Fused Library plugin instead. " +
          "See https://developer.android.com/build/publish-library/fused-library",
      )
    }

    // Apply the legacy plugin last.
    // Because we apply the ShadowJavaPlugin/ShadowApplication plugin in a withType callback for the
    // respective JavaPlugin/ApplicationPlugin, it may still apply before the shadowJar task is created etc.
    // If the user applies shadow before those plugins. However, this is fine, because this was also
    // the behavior with the old plugin when applying in that order.
    apply(LegacyShadowPlugin::class.java)
  }
}
