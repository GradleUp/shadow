package com.github.jengelman.gradle.plugins.shadow.testkit

import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.toPath

fun requireResourceAsStream(name: String): InputStream {
  return Utils::class.java.classLoader.getResourceAsStream(name)
    ?: throw NoSuchFileException("Resource $name not found.")
}

fun requireResourceAsUrl(name: String): URL {
  return Utils::class.java.classLoader.getResource(name)
    ?: throw NoSuchFileException("Resource $name not found.")
}

fun requireResourceAsPath(name: String): Path {
  val resource =
    Utils::class.java.classLoader.getResource(name)
      ?: throw NoSuchFileException("Resource $name not found.")
  val uri = resource.toURI()
  return if (uri.scheme == "file") {
    uri.toPath()
  } else {
    val tempFile =
      Files.createTempFile("resource-", name.substringAfterLast('/')).apply {
        toFile().deleteOnExit()
      }
    requireResourceAsStream(name).use { input ->
      Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
    }
    tempFile
  }
}

private object Utils
