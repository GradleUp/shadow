package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Not worth caching")
abstract class JavaJarExec extends JavaExec {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    File jarFile

    @Override
    @TaskAction
    void exec() {
        List<String> allArgs = [getJarFile().path] + getArgs()
        setArgs(allArgs)
        super.exec()
    }
}
