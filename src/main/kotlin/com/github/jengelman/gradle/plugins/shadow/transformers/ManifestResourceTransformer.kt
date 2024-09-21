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
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import org.apache.log4j.LogManager
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

@CacheableTransformer
class ManifestResourceTransformer : Transformer {
  private val logger = LogManager.getLogger(this::class.java)

  @Optional
  @Input
  var mainClass: String? = null

  @Optional
  @Input
  var manifestEntries: MutableMap<String, Attributes>? = null

  private var manifestDiscovered = false
  private var manifest: Manifest? = null

  override fun canTransformResource(element: FileTreeElement): Boolean {
    val path = element.relativePath.pathString
    return JarFile.MANIFEST_NAME.equals(path, ignoreCase = true)
  }

  override fun transform(context: TransformerContext) {
    if (!manifestDiscovered) {
      try {
        manifest = Manifest(context.inputStream)
        manifestDiscovered = true
      } catch (e: IOException) {
        logger.warn("Failed to read MANIFEST.MF", e)
      }
    }
  }

  override fun hasTransformedResource(): Boolean = true

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    if (manifest == null) {
      manifest = Manifest()
    }

    val attributes = manifest!!.mainAttributes

    mainClass?.let {
      attributes.put(Attributes.Name.MAIN_CLASS, it)
    }

    manifestEntries?.forEach { (key, value) ->
      attributes[Attributes.Name(key)] = value
    }

    val entry = ZipEntry(JarFile.MANIFEST_NAME).apply {
      time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, time)
    }
    os.putNextEntry(entry)
    manifest!!.write(os)
  }

  fun attributes(attributes: Map<String, *>): ManifestResourceTransformer {
    if (manifestEntries == null) {
      manifestEntries = mutableMapOf()
    }
    manifestEntries!!.putAll(attributes.mapValues { Attributes().apply { putValue(it.key, it.value.toString()) } })
    return this
  }
}
