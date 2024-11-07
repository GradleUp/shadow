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

import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.codehaus.plexus.util.IOUtil
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Input
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.util.jar.JarFile.MANIFEST_NAME
import org.slf4j.LoggerFactory

/**
 * A resource processor that can append arbitrary attributes to the first MANIFEST.MF
 * that is found in the set of JARs being processed. The attributes are appended in
 * the specified order, and duplicates are allowed.
 *
 * Modified from [ManifestResourceTransformer].
 * @author Chris Rankin
 */
public class ManifestAppenderTransformer : Transformer {
  private var manifestContents = ByteArray(0)
  private val _attributes = mutableListOf<Pair<String, Comparable<*>>>()
  private val log = LoggerFactory.getLogger(this::class.java)

  @get:Input
  public val attributes: List<Pair<String, Comparable<*>>> get() = _attributes

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return MANIFEST_NAME.equals(element.relativePath.pathString, ignoreCase = true)
  }

  override fun transform(context: TransformerContext) {
    if (manifestContents.isEmpty()) {
      manifestContents = IOUtil.toByteArray(context.inputStream)
      try {
        context.inputStream
      } catch (e: IOException) {
        log.warn("Failed to read MANIFEST.MF", e)
      }
    }
  }

  override fun hasTransformedResource(): Boolean = _attributes.isNotEmpty()

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    val entry = ZipEntry(MANIFEST_NAME)
    entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
    os.putNextEntry(entry)
    os.write(manifestContents)

    if (_attributes.isNotEmpty()) {
      for (attribute in _attributes) {
        os.write(attribute.first.toByteArray(UTF_8))
        os.write(SEPARATOR)
        os.write(attribute.second.toString().toByteArray(UTF_8))
        os.write(EOL)
      }
      os.write(EOL)
      _attributes.clear()
    }
  }

  public fun append(name: String, value: Comparable<*>): ManifestAppenderTransformer = apply {
    _attributes.add(Pair(name, value))
  }

  private companion object {
    private val EOL = "\r\n".toByteArray(UTF_8)
    private val SEPARATOR = ": ".toByteArray(UTF_8)
  }
}
