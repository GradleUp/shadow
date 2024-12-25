package com.github.jengelman.gradle.plugins.shadow.util

import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.outputStream

class AppendableJar(private val outputPath: Path) {
  private val contents = mutableMapOf<String, String>()

  fun insert(path: String, content: String): AppendableJar = apply {
    contents[path] = content
  }

  fun write(): Path {
    write(contents, outputPath.outputStream())
    return outputPath
  }

  companion object {
    fun write(contents: Map<String, String>, outputStream: OutputStream) {
      val builder = JarBuilder(outputStream)
      contents.forEach { (path, content) ->
        builder.withPath(path, content)
      }
      builder.build()
    }
  }
}
