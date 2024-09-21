/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.github.jengelman.gradle.plugins.shadow.transformers

import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.util.jar.JarFile.MANIFEST_NAME
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Input
import org.slf4j.LoggerFactory

@CacheableTransformer
class ManifestAppenderTransformer : Transformer {
  private val logger = LoggerFactory.getLogger(this::class.java)

  private val _attributes = mutableListOf<Pair<String, Comparable<*>>>()
  private var manifestContents = ByteArray(0)

  @Input
  val attributes: List<Pair<String, Comparable<*>>> = _attributes

  fun append(name: String, value: Comparable<*>): ManifestAppenderTransformer {
    _attributes.add(name to value)
    return this
  }

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return MANIFEST_NAME.equals(element.relativePath.pathString, ignoreCase = true)
  }

  override fun transform(context: TransformerContext) {
    if (manifestContents.isEmpty()) {
      try {
        manifestContents = context.inputStream.readBytes()
      } catch (e: IOException) {
        logger.warn("Failed to read MANIFEST.MF", e)
      }
    }
  }

  override fun hasTransformedResource(): Boolean = attributes.isNotEmpty()

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    val entry = ZipEntry(MANIFEST_NAME).apply {
      time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, time)
    }
    os.putNextEntry(entry)
    os.write(manifestContents)

    if (_attributes.isNotEmpty()) {
      _attributes.forEach { (name, value) ->
        os.write(name.toByteArray(UTF_8))
        os.write(": ".toByteArray(UTF_8))
        os.write(value.toString().toByteArray(UTF_8))
        os.write("\r\n".toByteArray(UTF_8))
      }
      os.write("\r\n".toByteArray(UTF_8))
      _attributes.clear()
    }
  }
}
