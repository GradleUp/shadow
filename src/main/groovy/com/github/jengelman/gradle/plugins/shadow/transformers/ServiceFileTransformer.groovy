/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
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

import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.api.tasks.util.PatternSet
import org.codehaus.plexus.util.IOUtil

/**
 * Modified from org.apache.maven.plugins.shade.resource.ServiceResourceTransformer.java
 *
 * Resources transformer that appends entries in META-INF/services resources into
 * a single resource. For example, if there are several META-INF/services/org.apache.maven.project.ProjectBuilder
 * resources spread across many JARs the individual entries will all be concatenated into a single
 * META-INF/services/org.apache.maven.project.ProjectBuilder resource packaged into the resultant JAR produced
 * by the shading process.
 *
 * Original
 * @author jvanzyl
 *
 * Modifications
 * @author Charlie Knudsen
 * @author John Engelman
 */
class ServiceFileTransformer implements Transformer, PatternFilterable {

    private static final String SERVICES_PATTERN = "META-INF/services/**"

    private static final String GROOVY_EXTENSION_MODULE_DESCRIPTOR_PATTERN =
            "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule"

    Map<String, ServiceStream> serviceEntries = [:].withDefault { new ServiceStream() }

    private final PatternSet patternSet =
            new PatternSet().include(SERVICES_PATTERN).exclude(GROOVY_EXTENSION_MODULE_DESCRIPTOR_PATTERN)

    void setPath(String path) {
        patternSet.setIncludes(["${path}/**"])
    }

    @Override
    boolean canTransformResource(FileTreeElement element) {
        return patternSet.asSpec.isSatisfiedBy(element)
    }

    @Override
    void transform(String path, InputStream is, List<Relocator> relocators) {
        def lines = is.readLines()
        relocators.each {rel ->
            if(rel.canRelocateClass(new File(path).name)) {
                path = rel.relocateClass(path)
            }
            lines.eachWithIndex { String line, int i ->
                if(rel.canRelocateClass(line)) {
                    lines[i] = rel.relocateClass(line)
                }
            }
        }
        lines.each {line -> serviceEntries[path].append(new ByteArrayInputStream(line.getBytes()))}
    }

    @Override
    boolean hasTransformedResource() {
        return serviceEntries.size() > 0
    }

    @Override
    void modifyOutputStream(ZipOutputStream os) {
        serviceEntries.each { String path, ServiceStream stream ->
            os.putNextEntry(new ZipEntry(path))
            IOUtil.copy(stream.toInputStream(), os)
            os.closeEntry()
        }
    }

    static class ServiceStream extends ByteArrayOutputStream {

        public ServiceStream(){
            super( 1024 )
        }

        public void append( InputStream is ) throws IOException {
            if ( count > 0 && buf[count - 1] != '\n' && buf[count - 1] != '\r' ) {
                byte[] newline = '\n'.bytes
                write(newline, 0, newline.length)
            }
            IOUtil.copy(is, this)
        }

        public InputStream toInputStream() {
            return new ByteArrayInputStream( buf, 0, count )
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    ServiceFileTransformer include(String... includes) {
        patternSet.include(includes)
        return this
    }

    /**
     * {@inheritDoc}
     */
    @Override
    ServiceFileTransformer include(Iterable<String> includes) {
        patternSet.include(includes)
        return this
    }

    /**
     * {@inheritDoc}
     */
    @Override
    ServiceFileTransformer include(Spec<FileTreeElement> includeSpec) {
        patternSet.include(includeSpec)
        return this
    }

    /**
     * {@inheritDoc}
     */
    @Override
    ServiceFileTransformer include(Closure includeSpec) {
        patternSet.include(includeSpec)
        return this
    }

    /**
     * {@inheritDoc}
     */
    @Override
    ServiceFileTransformer exclude(String... excludes) {
        patternSet.exclude(excludes)
        return this
    }

    /**
     * {@inheritDoc}
     */
    @Override
    ServiceFileTransformer exclude(Iterable<String> excludes) {
        patternSet.exclude(excludes)
        return this
    }

    /**
     * {@inheritDoc}
     */
    @Override
    ServiceFileTransformer exclude(Spec<FileTreeElement> excludeSpec) {
        patternSet.exclude(excludeSpec)
        return this
    }

    /**
     * {@inheritDoc}
     */
    @Override
    ServiceFileTransformer exclude(Closure excludeSpec) {
        patternSet.exclude(excludeSpec)
        return this
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Set<String> getIncludes() {
        return patternSet.includes
    }

    /**
     * {@inheritDoc}
     */
    @Override
    ServiceFileTransformer setIncludes(Iterable<String> includes) {
        patternSet.includes = includes
        return this
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Set<String> getExcludes() {
        return patternSet.excludes
    }

    /**
     * {@inheritDoc}
     */
    @Override
    ServiceFileTransformer setExcludes(Iterable<String> excludes) {
        patternSet.excludes = excludes
        return this
    }

}
