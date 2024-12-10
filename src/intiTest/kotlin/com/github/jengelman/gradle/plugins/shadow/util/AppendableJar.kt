package com.github.jengelman.gradle.plugins.shadow.util

import java.io.File

class AppendableJar(private val file: File) {
  private val contents = mutableMapOf<String, String>()

  fun insertFile(path: String, content: String): AppendableJar = apply {
    contents[path] = content
  }

  fun write(): File {
    val builder = JarBuilder(file.outputStream())
    contents.forEach { (path, content) ->
      builder.withFile(path, content)
    }
    builder.build()
    return file
  }
}
