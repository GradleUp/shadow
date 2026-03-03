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
import org.apache.commons.io.IOUtils
import org.apache.commons.io.output.CloseShieldOutputStream
import org.apache.logging.log4j.core.config.plugins.processor.PluginCache
import org.apache.logging.log4j.core.config.plugins.processor.PluginEntry
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement

import static org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor.PLUGIN_CACHE_FILE

/**
 * Modified from <a href="https://github.com/apache/logging-log4j-transform/blob/main/log4j-transform-maven-shade-plugin-extensions/src/main/java/org/apache/logging/log4j/maven/plugins/shade/transformer/Log4j2PluginCacheFileTransformer.java">Log4j2PluginCacheFileTransformer.java</a>.
 *
 * @author Paul Nelson Baker
 * @see <a href="https://www.linkedin.com/in/paul-n-baker/">LinkedIn</a>
 * @see <a href="https://github.com/paul-nelson-baker/">GitHub</a>
 */
@CacheableTransformer
class Log4j2PluginsCacheFileTransformer implements Transformer {

    /**
     * Log4j config files to share across the transformation stages.
     */
    private final List<File> temporaryFiles

    /**
     * {@link Relocator} instances to share across the transformation stages.
     */
    private final List<Relocator> relocators

    private ShadowStats stats

    Log4j2PluginsCacheFileTransformer() {
        temporaryFiles = new ArrayList<>()
        relocators = new ArrayList<>()
    }

    @Override
    boolean canTransformResource(FileTreeElement element) {
        return PLUGIN_CACHE_FILE == element.relativePath.pathString
    }

    @Override
    void transform(TransformerContext context) {
        def inputStream = context.is
        def temporaryFile = File.createTempFile("Log4j2Plugins", ".dat")
        temporaryFile.deleteOnExit()
        temporaryFiles.add(temporaryFile)
        FileOutputStream fos = new FileOutputStream(temporaryFile)
        try {
            IOUtils.copy(inputStream, fos)
        } finally {
            fos.close()
        }
        def contextRelocators = context.relocators
        if (contextRelocators != null) {
            this.relocators.addAll(contextRelocators)
        }
        if (this.stats == null) {
            this.stats = context.stats
        }
    }

    /**
     * @return {@code true} if any dat file collected.
     */
    @Override
    boolean hasTransformedResource() {
        return !temporaryFiles.isEmpty()
    }

    @Override
    void modifyOutputStream(ZipOutputStream zipOutputStream, boolean preserveFileTimestamps) {
        try {
            PluginCache aggregator = new PluginCache()
            aggregator.loadCacheFiles(getUrlEnumeration())
            relocatePlugins(aggregator)
            ZipEntry entry = new ZipEntry(PLUGIN_CACHE_FILE)
            entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
            zipOutputStream.putNextEntry(entry)
            // prevent the aggregator to close the jar output.
            aggregator.writeCache(CloseShieldOutputStream.wrap(zipOutputStream))
        } finally {
            deleteTempFiles()
        }
    }

    private Enumeration<URL> getUrlEnumeration() {
        def urls = temporaryFiles.collect({ it.toURI().toURL() }).asList()
        return Collections.enumeration(urls)
    }

    private void deleteTempFiles() {
        def iterator = temporaryFiles.listIterator()
        while (iterator.hasNext()) {
            def file = iterator.next()
            file.delete()
            iterator.remove()
        }
    }

    // Package-private for testing.
    void relocatePlugins(PluginCache pluginCache) {
        for (Map<String, PluginEntry> currentMap : pluginCache.getAllCategories().values()) {
            pluginEntryLoop:
            for (PluginEntry currentPluginEntry : currentMap.values()) {
                String className = currentPluginEntry.getClassName()
                RelocateClassContext relocateClassContext = new RelocateClassContext(className, stats)
                for (Relocator currentRelocator : relocators) {
                    // If we have a relocator that can relocate our current entry...
                    if (currentRelocator.canRelocateClass(className)) {
                        // Then we perform that relocation and update the plugin entry to reflect the new value.
                        String relocatedClassName = currentRelocator.relocateClass(relocateClassContext)
                        currentPluginEntry.setClassName(relocatedClassName)
                        continue pluginEntryLoop
                    }
                }
            }
        }
    }
}