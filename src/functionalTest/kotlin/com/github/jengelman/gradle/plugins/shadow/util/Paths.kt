package com.github.jengelman.gradle.plugins.shadow.util

import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

fun Path.prependText(text: String) = writeText(text + readText())

val String.invariantEolString: String get() = replace(System.lineSeparator(), "\n")

val String.variantSeparatorsPathString: String get() = replace("/", fileSystem.separator)

private val fileSystem = FileSystems.getDefault()
