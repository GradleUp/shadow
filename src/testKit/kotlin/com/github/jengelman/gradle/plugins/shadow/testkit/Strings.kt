package com.github.jengelman.gradle.plugins.shadow.testkit

import java.nio.file.FileSystems

val String.invariantEolString: String get() = replace(System.lineSeparator(), "\n")

val String.variantSeparatorsPathString: String get() = replace("/", fileSystem.separator)

private val fileSystem = FileSystems.getDefault()
