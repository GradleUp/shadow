package com.github.jengelman.gradle.plugins.shadow.tasks

import org.codehaus.groovy.reflection.ReflectionUtils
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * @deprecated This task will be removed in the next major release.
 */
@Deprecated
class KnowsTask extends DefaultTask {

    public static final String NAME = "knows"
    public static final String DESC = "Do you know who knows?"

    @TaskAction
    def knows() {
        logger.warn("The '{}' task has been deprecated and will be removed in the next major release.", NAME)
        println "\nNo, The Shadow Knows...."
        println ReflectionUtils.getCallingClass(0).getResourceAsStream("/shadowBanner.txt").text
    }
}
