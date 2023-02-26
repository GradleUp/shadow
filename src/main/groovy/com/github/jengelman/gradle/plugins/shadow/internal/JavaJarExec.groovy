package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction

class JavaJarExec extends JavaExec {

    @InputFile
    File jarFile

    @Override
    @TaskAction
    void exec() {
        List<String> allArgs = [getJarFile().path] + getArgs()
        setArgs(allArgs)
        super.exec()
    }
}
