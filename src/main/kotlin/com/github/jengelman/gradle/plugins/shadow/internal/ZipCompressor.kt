package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.File
import java.io.IOException
import org.apache.tools.zip.ZipOutputStream

/**
 * Compresses the input.
 *
 * Modified from [org.gradle.api.internal.file.copy.ZipCompressor.java](https://github.com/gradle/gradle/blob/73091267320cd330bcb3457903436579bac354ce/platforms/core-configuration/file-operations/src/main/java/org/gradle/api/internal/file/copy/ZipCompressor.java).
 */
public fun interface ZipCompressor {
  /**
   * Returns the output stream that is able to compress into the destination file
   *
   * @param destination the destination of the archive output stream
   */
  @Throws(IOException::class)
  public fun createArchiveOutputStream(destination: File): ZipOutputStream
}
