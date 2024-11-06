package com.github.jengelman.gradle.plugins.shadow.internal

import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction
import org.gradle.api.file.RegularFileProperty

public abstract class JavaJarExec public constructor() : JavaExec() {
  @get:InputFile
  public val jarFile: RegularFileProperty = objectFactory.fileProperty()

  @TaskAction
  override fun exec() {
    val allArgs = buildList<String> {
      add(jarFile.get().asFile.path)
      // Must cast args to List<String> here to avoid type mismatch.
      addAll(args as List<String>)
    }
    setArgs(allArgs)
    super.exec()
  }
}
