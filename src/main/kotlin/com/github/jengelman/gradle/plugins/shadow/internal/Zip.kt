package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.File
import org.apache.tools.zip.Zip64Mode
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.tasks.bundling.ZipEntryCompression

internal fun createZipOutputStream(
  destination: File,
  entryCompression: ZipEntryCompression,
  zip64: Boolean,
): ZipOutputStream {
  val method =
    when (entryCompression) {
      ZipEntryCompression.DEFLATED -> ZipOutputStream.DEFLATED
      ZipEntryCompression.STORED -> ZipOutputStream.STORED
    }
  val stream =
    if (method == ZipOutputStream.STORED) {
      ZipOutputStream(destination)
    } else {
      // Improve performance by avoiding lots of small writes to the file system.
      // STORED entries require a RandomAccessFile so their CRC can be updated after writing.
      ZipOutputStream(destination.outputStream().buffered())
    }
  return stream.apply {
    setUseZip64(if (zip64) Zip64Mode.AsNeeded else Zip64Mode.Never)
    setMethod(method)
  }
}

internal inline fun ZipOutputStream.writeEntry(
  name: String,
  preserveLastModified: Boolean,
  lastModified: Long = -1,
  unixMode: Int,
  write: ZipOutputStream.() -> Unit = {},
) {
  putNextEntry(
    zipEntry(name, preserveLastModified, lastModified) {
      this.unixMode = unixMode
    }
  )
  try {
    write()
  } finally {
    closeEntry()
  }

internal fun String.parentDirectoryEntries(): List<String> {
  val parents = mutableListOf<String>()
  var parent = substringBeforeLast('/', "")
  while (parent.isNotEmpty()) {
    parents += "$parent/"
    parent = parent.substringBeforeLast('/', "")
  }
  return parents.asReversed()
}
