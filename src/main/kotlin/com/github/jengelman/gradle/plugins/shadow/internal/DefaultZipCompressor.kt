package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.File
import org.apache.tools.zip.Zip64Mode
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.UncheckedIOException

internal class DefaultZipCompressor(
  allowZip64Mode: Boolean,
  private val entryCompressionMethod: Int,
) : ZipCompressor {
  private val zip64Mode = if (allowZip64Mode) Zip64Mode.AsNeeded else Zip64Mode.Never

  override fun createArchiveOutputStream(destination: File): ZipOutputStream {
    try {
      return ZipOutputStream(destination).apply {
        setUseZip64(zip64Mode)
        setMethod(entryCompressionMethod)
      }
    } catch (e: Exception) {
      throw UncheckedIOException("Unable to create ZIP output stream for file $destination.", e)
    }
  }
}
