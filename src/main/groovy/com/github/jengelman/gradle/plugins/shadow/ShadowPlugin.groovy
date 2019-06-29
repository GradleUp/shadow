package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaPlugin

import static java.util.Objects.nonNull

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

            rootProject.plugins.withId('com.gradle.build-scan') {
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

            afterEvaluate {
                plugins.withId('java-gradle-plugin') {
                    // needed to prevent inclusion of gradle-api into shadow JAR
                    configurations.compile.dependencies.remove dependencies.gradleApi()
                }
            }
        }
    }
}
