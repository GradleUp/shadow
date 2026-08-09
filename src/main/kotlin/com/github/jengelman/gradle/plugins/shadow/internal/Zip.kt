package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.CONSTANT_TIME_FOR_ZIP_ENTRIES
import java.io.File
import org.apache.tools.zip.UnixStat
import org.apache.tools.zip.Zip64Mode
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.gradle.api.tasks.bundling.ZipEntryCompression

@JvmInline
internal value class UnixMode private constructor(internal val value: Int) {
  companion object {
    fun directory(permissions: Int = UnixStat.DEFAULT_DIR_PERM): UnixMode =
      UnixMode(UnixStat.DIR_FLAG or permissions)

    fun file(permissions: Int = UnixStat.DEFAULT_FILE_PERM): UnixMode =
      UnixMode(UnixStat.FILE_FLAG or permissions)

    fun raw(mode: Int): UnixMode = UnixMode(mode)
  }
}

internal fun createZipOutputStream(
  destination: File,
  entryCompression: ZipEntryCompression,
  zip64: Boolean,
  encoding: String?,
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
    encoding?.let(::setEncoding)
  }
}

internal inline fun ZipOutputStream.writeEntry(
  name: String,
  preserveLastModified: Boolean = true,
  lastModified: Long = -1,
  unixMode: UnixMode? = null,
  write: ZipOutputStream.() -> Unit = {},
) {
  if (name.split('/', '\\').any { it == ".." }) {
    throw GradleException("Malicious ZIP entry containing path traversal sequence: $name")
  }

  val entry =
    ZipEntry(name).apply {
      time =
        if (preserveLastModified && lastModified >= 0) {
          lastModified
        } else {
          CONSTANT_TIME_FOR_ZIP_ENTRIES
        }
      if (unixMode != null) {
        this.unixMode = unixMode.value
      }
    }
  putNextEntry(entry)
  try {
    write()
  } finally {
    closeEntry()
  }
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
