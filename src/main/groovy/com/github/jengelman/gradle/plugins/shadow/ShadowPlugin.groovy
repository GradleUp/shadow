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
            plugins.apply(LegacyShadowPlugin)
            plugins.withType(JavaPlugin).configureEach {
                plugins.apply(ShadowJavaPlugin)
            }
            plugins.withType(ApplicationPlugin).configureEach {
                plugins.apply(ShadowApplicationPlugin)
            }

            // Legacy build scan support for Gradle Enterprise, users should migrate to develocity plugin.
            rootProject.plugins.withId('com.gradle.enterprise') {
                configureBuildScan(rootProject)
            }
            rootProject.plugins.withId('com.gradle.develocity') {
                configureBuildScan(rootProject)
            }
        }
    }

    private void configureBuildScan(Project rootProject) {
        rootProject.buildScan.buildFinished {
            def shadowTasks = tasks.withType(ShadowJar)
            shadowTasks.each { task ->
                if (task.didWork) {
                    task.stats.buildScanData.each { k, v ->
                        rootProject.buildScan.value "shadow.${task.path}.${k}", v.toString()
                    }
                    rootProject.buildScan.value "shadow.${task.path}.configurations", task.configurations*.name.join(", ")
                }
            }
        }
    }
}
