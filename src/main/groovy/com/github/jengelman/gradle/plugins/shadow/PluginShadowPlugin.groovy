package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Plugin
import org.gradle.api.Project

class PluginShadowPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply(ShadowPlugin)

        project.tasks.withType(ShadowJar) { ShadowJar task ->
            if (task.name == ShadowJavaPlugin.SHADOW_JAR_TASK_NAME) {
                ConfigureShadowRelocation relocate = project.tasks.create(ConfigureShadowRelocation.taskName(project.tasks.shadowJar), ConfigureShadowRelocation)
                relocate.target = (ShadowJar) project.tasks.shadowJar

                project.tasks.shadowJar.dependsOn relocate
            }
        }
    }
}
