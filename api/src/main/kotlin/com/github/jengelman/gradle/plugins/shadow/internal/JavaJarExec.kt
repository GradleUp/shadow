package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction
import java.io.File

public abstract class JavaJarExec public constructor() : JavaExec() {
  @get:InputFile
  public lateinit var jarFile: File

  @TaskAction
  override fun exec() {
    val allArgs = listOf(jarFile.path) + args
    setArgs(allArgs)
    super.exec()
  }
}
