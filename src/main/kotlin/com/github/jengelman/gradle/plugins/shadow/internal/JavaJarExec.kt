package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.File
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction

internal abstract class JavaJarExec : JavaExec() {

  @get:InputFile
  abstract val jarFile: File

  @TaskAction
  override fun exec() {
    val allArgs = listOf(jarFile.path) + args
    setArgs(allArgs)
    super.exec()
  }
}
