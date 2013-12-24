package com.github.jengelman.gradle.plugins.shadow

import org.gradle.api.artifacts.Configuration
import com.github.jengelman.gradle.plugins.shadow.tasks.KnowsTask
import com.github.jengelman.gradle.plugins.shadow.tasks.OutputSignedLibsTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowTask

class ShadowPlugin implements Plugin<Project>{

    static final String GROUP = "Shadow"

    @Override
    public void apply(Project project) {
        project.plugins.apply JavaPlugin
        addShadow(project)
    }

    void addShadow(Project project) {

        ["compile", "runtime"].each { config ->
            Configuration signed = project.configurations.create "signed${config.capitalize()}"
            Configuration original = project.configurations.getByName config
            original.extendsFrom = (original.extendsFrom + signed) as Set
        }

        project.extensions.create(ShadowExtension.NAME, ShadowExtension, project)

        KnowsTask knows = project.tasks.create(KnowsTask.NAME, KnowsTask)
        knows.description = KnowsTask.DESC
        knows.group = GROUP

        OutputSignedLibsTask signedCopyTask = project.tasks.create(OutputSignedLibsTask.NAME, OutputSignedLibsTask)
        signedCopyTask.description = OutputSignedLibsTask.DESC
        signedCopyTask.group = GROUP
        signedCopyTask.from project.configurations.signedCompile
        signedCopyTask.from project.configurations.signedRuntime
        signedCopyTask.into project.shadow.signedLibsDir

        ShadowTask shadow = project.tasks.create(ShadowTask.NAME, ShadowTask)
        shadow.description = ShadowTask.DESC
        shadow.group = GROUP
        shadow.dependsOn project.tasks.jar, signedCopyTask
    }
}
