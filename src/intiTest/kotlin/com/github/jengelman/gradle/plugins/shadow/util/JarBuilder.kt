package com.github.jengelman.gradle.plugins.shadow.util

import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.outputStream

class JarBuilder(
  private val outputPath: Path,
  private val contents: MutableMap<String, String> = mutableMapOf(),
) {
  private val entries = mutableSetOf<String>()
  private val jos = JarOutputStream(outputPath.outputStream())

  fun insert(path: String, content: String): JarBuilder = apply {
    contents[path] = content
  }

  fun write(): Path {
    jos.use {
      contents.forEach { (entry, content) ->
        val idx = entry.lastIndexOf('/')
        if (idx != -1) {
          addDirectory(entry.substring(0, idx))
        }
        if (entries.add(entry)) {
          jos.putNextEntry(JarEntry(entry))
          content.byteInputStream().copyTo(jos)
        }
      }
    }
    return outputPath
  }

  private fun addDirectory(name: String) {
    if (entries.add(name)) {
      val parent = name.substringBeforeLast('/', "")
      if (parent.isNotEmpty() && !entries.contains(parent)) {
        addDirectory(parent)
      }
      // directory entries must end in "/"
      jos.putNextEntry(JarEntry("$name/"))
    }
  }
}
