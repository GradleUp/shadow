package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.legacy.LegacyShadowPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaPlugin

class ShadowPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.with {
            plugins.apply(ShadowBasePlugin)
            plugins.apply(LegacyShadowPlugin)
            plugins.withType(JavaPlugin) {
                plugins.apply(ShadowJavaPlugin)
            }
            plugins.withType(ApplicationPlugin) {
                plugins.apply(ShadowApplicationPlugin)
            }
        }
    }
}
