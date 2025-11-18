package com.github.jengelman.gradle.plugins.shadow.util

import java.io.File
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

fun Path.prependText(text: String) = writeText(text + readText())

val String.invariantEolString: String get() = replace(System.lineSeparator(), "\n")

val String.invariantSeparatorsPathString: String get() = replace(File.separator, "/")
