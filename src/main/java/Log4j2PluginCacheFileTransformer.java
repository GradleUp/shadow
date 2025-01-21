/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.logging.log4j.core.config.plugins.processor.PluginCache;
import org.apache.logging.log4j.core.config.plugins.processor.PluginEntry;
import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.resource.ReproducibleResourceTransformer;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor.PLUGIN_CACHE_FILE;

/**
 * 'log4j-maven-shade-plugin' transformer implementation.
 */
public class Log4j2PluginCacheFileTransformer implements ReproducibleResourceTransformer {

    /**
     * Log4j config files to share across the transformation stages.
     */
    private final List<Path> tempFiles;
    /**
     * {@link Relocator} instances to share across the transformation stages.
     */
    private final List<Relocator> tempRelocators;
    /**
     * Store youngest (i.e. largest millisecond) so that we can produce reproducible jar file
     */
    private long youngestTime = 0;

    /**
     * Default constructor, initializing internal state.
     */
    public Log4j2PluginCacheFileTransformer() {
        tempRelocators = new ArrayList<>();
        tempFiles = new ArrayList<>();
    }

    /**
     * @param resource resource to check
     * @return true when resource is recognized as log4j-plugin-cache file
     */
    @Override
    public boolean canTransformResource(final String resource) {
        return PLUGIN_CACHE_FILE.equals(resource);
    }

    @Override
    @Deprecated
    public void processResource(String resource, InputStream is, List<Relocator> relocators) {
        // stub
    }

    /**
     * @param resource      ignored parameter
     * @param resourceInput resource input stream to save in temp file
     *                      for next stage
     * @param relocators    relocators to keep for next stage
     * @throws IOException thrown by file writing errors
     */
    @Override
    public void processResource(
            final String resource, final InputStream resourceInput, final List<Relocator> relocators, final long time)
            throws IOException {
        final Path tempFile = Files.createTempFile("Log4j2Plugins", "dat");
        Files.copy(resourceInput, tempFile, REPLACE_EXISTING);
        tempFiles.add(tempFile);
        youngestTime = Math.max(youngestTime, time);

        if (relocators != null) {
            this.tempRelocators.addAll(relocators);
        }
    }

    /**
     * @return true if any dat file collected
     */
    @Override
    public boolean hasTransformedResource() {
        return tempFiles.size() > 0;
    }

    /**
     * Stores all previously collected log4j-cache-files to the target jar.
     *
     * @param jos jar output
     * @throws IOException When the IO blows up
     */
    @Override
    public void modifyOutputStream(final JarOutputStream jos) throws IOException {
        try {
            final PluginCache aggregator = new PluginCache();
            aggregator.loadCacheFiles(getUrls());
            relocatePlugin(tempRelocators, aggregator.getAllCategories());
            putJarEntry(jos);
            // prevent the aggregator to close the jar output
            final CloseShieldOutputStream outputStream = new CloseShieldOutputStream(jos);
            aggregator.writeCache(outputStream);
        } finally {
            deleteTempFiles();
        }
    }

    private Enumeration<URL> getUrls() throws MalformedURLException {
        final List<URL> urls = new ArrayList<>();
        for (final Path tempFile : tempFiles) {
            final URL url = tempFile.toUri().toURL();
            urls.add(url);
        }
        return Collections.enumeration(urls);
    }

    /**
     * Applies the given {@code relocators} to the {@code aggregator}.
     *
     * @param relocators           relocators.
     * @param aggregatorCategories all categories of the aggregator
     */
    /* default */ void relocatePlugin(
            final List<Relocator> relocators, Map<String, Map<String, PluginEntry>> aggregatorCategories) {
        for (final Entry<String, Map<String, PluginEntry>> categoryEntry : aggregatorCategories.entrySet()) {
            for (final Entry<String, PluginEntry> pluginMapEntry :
                    categoryEntry.getValue().entrySet()) {
                final PluginEntry pluginEntry = pluginMapEntry.getValue();
                final String originalClassName = pluginEntry.getClassName();

                final Relocator matchingRelocator = findFirstMatchingRelocator(originalClassName, relocators);

                if (matchingRelocator != null) {
                    final String newClassName = matchingRelocator.relocateClass(originalClassName);
                    pluginEntry.setClassName(newClassName);
                }
            }
        }
    }

    private Relocator findFirstMatchingRelocator(final String originalClassName, final List<Relocator> relocators) {
        Relocator result = null;
        for (final Relocator relocator : relocators) {
            if (relocator.canRelocateClass(originalClassName)) {
                result = relocator;
                break;
            }
        }
        return result;
    }

    private void putJarEntry(JarOutputStream jos) throws IOException {
        final JarEntry jarEntry = new JarEntry(PLUGIN_CACHE_FILE);

        // Set time to youngest timestamp, to ensure reproducible output.
        final FileTime fileTime = FileTime.fromMillis(youngestTime);
        jarEntry.setLastModifiedTime(fileTime);

        jos.putNextEntry(jarEntry);
    }

    private void deleteTempFiles() throws IOException {
        final ListIterator<Path> pathIterator = tempFiles.listIterator();
        while (pathIterator.hasNext()) {
            final Path path = pathIterator.next();
            Files.deleteIfExists(path);
            pathIterator.remove();
        }
    }
}
