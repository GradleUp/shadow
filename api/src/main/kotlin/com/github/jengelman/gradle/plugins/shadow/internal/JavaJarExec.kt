package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction

internal abstract class JavaJarExec : JavaExec() {
    @get:InputFile
    abstract val jarFile: RegularFileProperty

    @TaskAction
    override fun exec() {
        val allArgs = buildList {
            add(jarFile.get().asFile.path)
            // Must cast args to List<String> here to avoid type mismatch.
            addAll(args as List<String>)
        }
        setArgs(allArgs)
        super.exec()
    }
}
