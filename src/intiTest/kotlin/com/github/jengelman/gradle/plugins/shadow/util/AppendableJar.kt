package com.github.jengelman.gradle.plugins.shadow.util

import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.outputStream

class AppendableJar(initialContents: Map<String, String>) {
  private val contents = initialContents.toMutableMap()
  private lateinit var outputPath: Path

  constructor(outputPath: Path) : this(emptyMap()) {
    this.outputPath = outputPath
  }

  fun insert(path: String, content: String): AppendableJar = apply {
    contents[path] = content
  }

  fun write(): Path {
    write(outputPath.outputStream())
    return outputPath
  }

  fun write(outputStream: OutputStream) {
    val builder = JarBuilder(outputStream)
    contents.forEach { (path, content) ->
      builder.withPath(path, content)
    }
    builder.build()
  }
}
