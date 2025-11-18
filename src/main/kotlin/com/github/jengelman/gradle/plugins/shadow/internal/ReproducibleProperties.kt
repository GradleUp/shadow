package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.OutputStream
import java.io.StringWriter
import java.io.Writer
import java.nio.charset.Charset
import java.util.Properties
import java.util.TreeMap

/**
 * Provides functionality for reproducible serialization.
 */
internal class ReproducibleProperties : Properties() {
  // Just to prevent accidental misuse.
  override fun store(writer: Writer?, comments: String?) {
    throw UnsupportedOperationException("use writeWithoutComments()")
  }

  // Just to prevent accidental misuse.
  override fun store(out: OutputStream?, comments: String?) {
    throw UnsupportedOperationException("use writeWithoutComments()")
  }

  fun writeWithoutComments(charset: Charset, os: OutputStream) {
    val bufferedReader = StringWriter().apply {
      super.store(this, null)
    }.toString().reader().buffered()

    os.bufferedWriter(charset).apply {
      var line: String? = null
      while (bufferedReader.readLine().also { line = it } != null) {
        if (!line!!.startsWith("#")) {
          write(line)
          newLine()
        }
      }
    }.flush()
  }

  // yields the entries for Properties.store0() in sorted order
  override val entries: MutableSet<MutableMap.MutableEntry<Any, Any>>
    get() {
      val sorted = TreeMap<Any, Any>()
      super.entries.forEach { sorted[it.key] = it.value }
      return sorted.entries
    }
}
