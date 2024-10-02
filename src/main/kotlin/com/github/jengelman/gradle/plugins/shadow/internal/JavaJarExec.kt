package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction

internal abstract class JavaJarExec : JavaExec() {

  @get:InputFile
  val jarFile: RegularFileProperty = objectFactory.fileProperty()

  @TaskAction
  override fun exec() {
    val allArgs = listOf(jarFile.get().asFile.path) + args
    setArgs(allArgs)
    super.exec()
  }
}
