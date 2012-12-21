package org.gradle.api.plugins.shadow

import org.gradle.api.plugins.shadow.tasks.KnowsTask
import org.gradle.api.plugins.shadow.tasks.ShadowTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin

class ShadowPlugin implements Plugin<Project>{

    static final String SHADOW_WAR_TASK = "slimWar"
    static final String SHADOW_WAR_TASK_DESC = "Creates war file with all dependencies and classes bundled inside one JAR"

    static final String GROUP = "Shadow"

    @Override
    public void apply(Project project) {
        project.plugins.apply JavaPlugin
        addShadow(project)
        
//        if(project.plugins.hasPlugin(WarPlugin)){
//            War slimWar = project.tasks.add(SHADOW_WAR_TASK, War)
//            slimWar.description = SHADOW_WAR_TASK_DESC
//            slimWar.group = SHADOW_GROUP
//            slimWar.dependsOn shadow
//
//            slimWar.conventionMapping.map("classpath") {
//                project.files(fatJar.archivePath) +  project.configurations.runtime.copyRecursive {
//                    it.ext.has('shadowExclude') && it.ext.get('shadowExclude')
//                }
//            }
//
//        }
    }

    void addShadow(Project project) {
        project.extensions.create(ShadowTaskExtension.NAME, ShadowTaskExtension, project)
        ShadowTask shadow = project.tasks.add(ShadowTask.NAME, ShadowTask)
        shadow.description = ShadowTask.DESC
        shadow.group = GROUP
        shadow.dependsOn project.tasks.jar

        KnowsTask knows = project.tasks.add(KnowsTask.NAME, KnowsTask)
        knows.description = KnowsTask.DESC
        knows.group = GROUP
    }
}
