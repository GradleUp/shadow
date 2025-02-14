package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.File
import org.apache.tools.zip.Zip64Mode
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.UncheckedIOException

/**
 * Modified from [org.gradle.api.internal.file.copy.DefaultZipCompressor.java](https://github.com/gradle/gradle/blob/b893c2b085046677cf858fb3d5ce00e68e556c3a/platforms/core-configuration/file-operations/src/main/java/org/gradle/api/internal/file/copy/DefaultZipCompressor.java).
 */
internal class DefaultZipCompressor(
  allowZip64Mode: Boolean,
  private val entryCompressionMethod: Int,
) : ZipCompressor {
  private val zip64Mode = if (allowZip64Mode) Zip64Mode.AsNeeded else Zip64Mode.Never

  override fun createArchiveOutputStream(destination: File): ZipOutputStream {
    return try {
      ZipOutputStream(destination).apply {
        setUseZip64(zip64Mode)
        setMethod(entryCompressionMethod)
      }
    } catch (e: Exception) {
      throw UncheckedIOException("Unable to create ZIP output stream for file $destination.", e)
    }
  }
}
