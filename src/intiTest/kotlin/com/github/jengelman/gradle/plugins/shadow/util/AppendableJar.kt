package com.github.jengelman.gradle.plugins.shadow.util

import java.io.File
import java.io.OutputStream

class AppendableJar(initialContents: Map<String, String>) {
  private val contents = initialContents.toMutableMap()
  private lateinit var outputFile: File

  constructor(outputFile: File) : this(emptyMap()) {
    this.outputFile = outputFile
  }

  fun insert(path: String, content: String): AppendableJar = apply {
    contents[path] = content
  }

  fun write(): File {
    write(outputFile.outputStream())
    return outputFile
  }

  fun write(outputStream: OutputStream) {
    val builder = JarBuilder(outputStream)
    contents.forEach { (path, content) ->
      builder.withFile(path, content)
    }
    builder.build()
  }
}
