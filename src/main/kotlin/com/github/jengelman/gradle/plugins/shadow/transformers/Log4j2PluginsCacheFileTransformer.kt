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

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.Collections
import java.util.Enumeration
import org.apache.commons.io.IOUtils
import org.apache.commons.io.output.CloseShieldOutputStream
import org.apache.logging.log4j.core.config.plugins.processor.PluginCache
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement

@CacheableTransformer
class Log4j2PluginsCacheFileTransformer : Transformer {

  private val temporaryFiles: MutableList<File> = mutableListOf()
  private val relocators: MutableList<Relocator> = mutableListOf()
  private var stats: ShadowStats? = null

  override fun canTransformResource(element: FileTreeElement): Boolean {
    return PLUGIN_CACHE_FILE == element.name
  }

  override fun transform(context: TransformerContext) {
    val inputStream = context.inputStream
    val temporaryFile = File.createTempFile("Log4j2Plugins", ".dat")
    temporaryFile.deleteOnExit()
    temporaryFiles.add(temporaryFile)
    FileOutputStream(temporaryFile).use { fos ->
      IOUtils.copy(inputStream, fos)
    }
    relocators.addAll(context.relocators)
    if (stats == null) {
      stats = context.stats
    }
  }

  override fun hasTransformedResource(): Boolean {
    val hasTransformedMultipleFiles = temporaryFiles.size > 1
    val hasAtLeastOneFileAndRelocator = temporaryFiles.isNotEmpty() && relocators.isNotEmpty()
    return hasTransformedMultipleFiles || hasAtLeastOneFileAndRelocator
  }

  override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
    val pluginCache = PluginCache()
    pluginCache.loadCacheFiles(urlEnumeration)
    relocatePlugins(pluginCache)
    val entry = ZipEntry(PLUGIN_CACHE_FILE).apply {
      time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, time)
    }
    os.putNextEntry(entry)
    pluginCache.writeCache(CloseShieldOutputStream.wrap(os))
    temporaryFiles.clear()
  }

  private val urlEnumeration: Enumeration<URL>
    get() {
      val urls = temporaryFiles.map { it.toURI().toURL() }
      return Collections.enumeration(urls)
    }

  private fun relocatePlugins(pluginCache: PluginCache) {
    for (currentMap in pluginCache.allCategories.values) {
      for (currentPluginEntry in currentMap.values) {
        val className = currentPluginEntry.className
        val relocateClassContext = RelocateClassContext(className, stats!!)
        for (currentRelocator in relocators) {
          if (currentRelocator.canRelocateClass(className)) {
            val relocatedClassName = currentRelocator.relocateClass(relocateClassContext)
            currentPluginEntry.className = relocatedClassName
            break
          }
        }
      }
    }
  }

  companion object {
    private const val PLUGIN_CACHE_FILE = "org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat"
  }
}
