package com.github.jengelman.gradle.plugins.shadow.testkit

import java.io.InputStream
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.toPath

fun requireResourceAsStream(name: String): InputStream {
  return Utils::class.java.classLoader.getResourceAsStream(name)
    ?: throw NoSuchFileException("Resource $name not found.")
}

fun requireResourceAsPath(name: String): Path {
  val resource = Utils::class.java.classLoader.getResource(name)
    ?: throw NoSuchFileException("Resource $name not found.")
  return resource.toURI().toPath()
}

private object Utils
