package org.gradle.api.plugins.shadow

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin

class ShadowPlugin implements Plugin<Project> {

    @Override
    void apply(Project t) {
        project.apply(plugin: JavaBasePlugin)
    }
}
