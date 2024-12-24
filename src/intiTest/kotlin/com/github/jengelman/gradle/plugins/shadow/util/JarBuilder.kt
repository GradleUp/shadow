package com.github.jengelman.gradle.plugins.shadow.util

import java.io.OutputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class JarBuilder(os: OutputStream) {
  private val entries = mutableSetOf<String>()
  private val jos = JarOutputStream(os)

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

  fun withFile(path: String, data: String): JarBuilder {
    val idx = path.lastIndexOf('/')
    if (idx != -1) {
      addDirectory(path.substring(0, idx))
    }
    if (entries.add(path)) {
      jos.putNextEntry(JarEntry(path))
      data.byteInputStream().copyTo(jos)
    }
    return this
  }

  fun build() {
    jos.close()
  }
}
