package com.github.jengelman.gradle.plugins.shadow.tasks

import org.codehaus.groovy.reflection.ReflectionUtils
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class KnowsTask extends DefaultTask {

    static final String NAME = "knows"
    static final String DESC = "Do you know who knows?"

    @TaskAction
    def knows() {
        println "\nNo, The Shadow Knows...."
        println ReflectionUtils.getCallingClass(0).getResourceAsStream("/shadowBanner.txt").text
    }
}
