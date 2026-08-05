package com.github.jengelman.gradle.plugins.shadow.testkit

import java.nio.file.FileSystems

val String.invariantEolString: String
  get() = replace("\r\n", "\n")

val String.variantSeparatorsPathString: String
  get() = replace("/", fileSystem.separator)

private val fileSystem = FileSystems.getDefault()
