package com.github.jengelman.gradle.plugins.shadow

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaPlugin

class ShadowPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply(ShadowBasePlugin)
        project.plugins.withType(JavaPlugin) {
            project.plugins.apply(ShadowJavaPlugin)
        }
        project.plugins.withType(ApplicationPlugin) {
            project.plugins.apply(ShadowApplicationPlugin)
        }
    }
}
