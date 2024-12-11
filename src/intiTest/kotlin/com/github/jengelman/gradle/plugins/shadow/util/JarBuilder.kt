package com.github.jengelman.gradle.plugins.shadow.util

import java.io.OutputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class JarBuilder(os: OutputStream) {
  private val entries = mutableListOf<String>()
  private val jos = JarOutputStream(os)

  private fun addDirectory(name: String) {
    if (!entries.contains(name)) {
      val parent = name.substringBeforeLast('/', "")
      if (parent.isNotEmpty() && !entries.contains(parent)) {
        addDirectory(parent)
      }

      // directory entries must end in "/"
      val entry = JarEntry("$name/")
      jos.putNextEntry(entry)
      entries.add(name)
    }
  }

  fun withFile(path: String, data: String): JarBuilder {
    val idx = path.lastIndexOf('/')
    if (idx != -1) {
      addDirectory(path.substring(0, idx))
    }
    if (!entries.contains(path)) {
      val entry = JarEntry(path)
      jos.putNextEntry(entry)
      entries.add(path)
      data.byteInputStream().use { it.copyTo(jos) }
    }
    return this
  }

  fun build() {
    jos.close()
  }
}
