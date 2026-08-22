package com.github.jengelman.gradle.plugins.shadow.util

import com.github.jengelman.gradle.plugins.shadow.testkit.JarPath
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

fun Path.prependText(text: String) = writeText("$text${readText()}")

fun JarPath.classLoader(
  parent: ClassLoader? = ClassLoader.getSystemClassLoader().parent
): URLClassLoader {
  val url = use { it.toUri().toURL() }
  return URLClassLoader(arrayOf(url), parent)
}
