package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.legacy.LegacyShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaPlugin

class ShadowPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.with {
            plugins.apply(ShadowBasePlugin)
            plugins.withType(JavaPlugin) {
                plugins.apply(ShadowJavaPlugin)
            }
            plugins.withType(ApplicationPlugin) {
                plugins.apply(ShadowApplicationPlugin)
            }
            // Apply the legacy plugin last
            //   Because we apply the ShadowJavaPlugin/ShadowApplication plugin in a withType callback for the
            //   respective JavaPlugin/ApplicationPlugin, it may still apply before the shadowJar task is created and
            //   etc. if the user applies shadow before those plugins. However, this is fine, because this was also
            //   the behavior with the old plugin when applying in that order.
            plugins.apply(LegacyShadowPlugin)
        }
    }
}
