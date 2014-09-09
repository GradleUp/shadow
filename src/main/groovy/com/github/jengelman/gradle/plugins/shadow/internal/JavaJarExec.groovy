package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

class JavaJarExec extends JavaExec {

    @InputFile
    File jarFile

    @Override
    @TaskAction
    public void exec() {
        setMain('-jar')
        List<String> allArgs = [getJarFile().path] + getArgs()
        setArgs(allArgs)
        super.exec()
    }
}
