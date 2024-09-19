/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.jengelman.gradle.plugins.shadow.internal

import java.io.File
import org.apache.tools.zip.Zip64Mode
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.UncheckedIOException

internal class DefaultZipCompressor(
  allowZip64Mode: Boolean,
  private val entryCompressionMethod: Int,
) : ZipCompressor {
  private val zip64Mode: Zip64Mode = if (allowZip64Mode) Zip64Mode.AsNeeded else Zip64Mode.Never

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
