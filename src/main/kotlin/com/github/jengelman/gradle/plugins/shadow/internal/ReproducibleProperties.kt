package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.OutputStream
import java.io.StringWriter
import java.io.Writer
import java.nio.charset.Charset
import java.util.Properties

/** Provides functionality for reproducible serialization. */
internal class ReproducibleProperties : Properties() {
  override fun store(writer: Writer, comments: String) {
    throw UnsupportedOperationException("use writeWithoutComments()")
  }

  override fun store(out: OutputStream, comments: String?) {
    throw UnsupportedOperationException("use writeWithoutComments()")
  }

  fun writeWithoutComments(charset: Charset, os: OutputStream) {
    val bufferedReader =
      StringWriter().apply { super.store(this, null) }.toString().reader().buffered()

    os
      .bufferedWriter(charset)
      .apply {
        var line: String? = null
        while (bufferedReader.readLine().also { line = it } != null && line != null) {
          if (!line.startsWith("#")) {
            write(line)
            newLine()
          }
        }
      }
      .flush()
  }

  override val entries: MutableSet<MutableMap.MutableEntry<Any, Any>>
    // Yields the entries for Properties.store0() in sorted order.
    get() = super.entries.toSortedSet(compareBy { it.key.toString() })
}
