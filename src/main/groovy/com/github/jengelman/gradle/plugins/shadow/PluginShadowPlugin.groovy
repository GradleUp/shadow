package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Plugin
import org.gradle.api.Project

class PluginShadowPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply(ShadowPlugin)

        project.tasks.withType(ShadowJar).configureEach { ShadowJar task ->
            if (task.name == ShadowJavaPlugin.SHADOW_JAR_TASK_NAME) {
                project.tasks.register(ConfigureShadowRelocation.taskName(task), ConfigureShadowRelocation) { relocate ->
                    relocate.target = (ShadowJar) task

                    task.dependsOn relocate
                }
            }
        }
    }
}
