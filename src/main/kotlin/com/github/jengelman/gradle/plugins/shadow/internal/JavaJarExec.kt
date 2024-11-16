package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.File
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction

public abstract class JavaJarExec : JavaExec() {
  @get:InputFile
  public lateinit var jarFile: File

  @TaskAction
  override fun exec() {
    val allArgs = buildList {
      add(jarFile.path)
      // Must cast args to List<String> here to avoid type mismatch.
      addAll(args as List<String>)
    }
    setArgs(allArgs)
    super.exec()
  }
}
