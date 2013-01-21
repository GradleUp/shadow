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
package org.gradle.api.plugins.shadow.transformers

import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.RelativePath
import org.gradle.api.plugins.shadow.relocator.Relocator
import org.gradle.mvn3.org.codehaus.plexus.util.IOUtil

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

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
class ServiceFileTransformer implements Transformer {

    private static final String SERVICES_PATH = "META-INF/services";
    Map<RelativePath, ServiceStream> serviceEntries = [:]

    @Override
    boolean canTransformResource(FileTreeElement entry) {
        return entry.relativePath.pathString.contains(SERVICES_PATH)
    }

    @Override
    void transform(FileTreeElement entry, InputStream is, List<Relocator> relocators) {
        ServiceStream out = serviceEntries[entry.relativePath]
        if ( out == null ) {
            out = new ServiceStream()
            serviceEntries[entry.relativePath] = out
        }
        out.append(is)
    }

    @Override
    boolean hasTransformedResource() {
        return serviceEntries.size() > 0
    }

    @Override
    void modifyOutputStream(JarOutputStream jos) {
        serviceEntries.each { RelativePath path, ServiceStream stream ->
            jos.putNextEntry(new JarEntry(path.pathString))
            IOUtil.copy(stream.toInputStream(), jos)
            jos.closeEntry()
        }
    }

    static class ServiceStream extends ByteArrayOutputStream{

        public ServiceStream(){
            super( 1024 );
        }

        public void append( InputStream is ) throws IOException {
            if ( count > 0 && buf[count - 1] != '\n' && buf[count - 1] != '\r' ) {
                byte[] newline = '\n'.bytes;
                write(newline, 0, newline.length);
            }
            IOUtil.copy(is, this);
        }

        public InputStream toInputStream() {
            return new ByteArrayInputStream( buf, 0, count );
        }
    }
}
