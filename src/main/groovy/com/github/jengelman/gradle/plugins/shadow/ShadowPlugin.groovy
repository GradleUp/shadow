package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
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

        project.plugins.withId('com.gradle.build-scan') {
            project.buildScan.buildFinished {
                def shadowTasks = project.tasks.withType(ShadowJar)
                shadowTasks.each { task ->
                    if (task.didWork) {
                        task.stats.buildScanData.each { k, v ->
                            project.buildScan.value "shadow.${task.name}.${k}", v.toString()
                        }
                        project.buildScan.value "shadow.${task.name}.configurations", task.configurations*.name.join(", ")
                    }
                }
            }
        }
    }
}
