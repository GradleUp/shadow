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
import org.apache.commons.collections.map.MultiValueMap
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang.StringUtils
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement
import org.gradle.mvn3.org.codehaus.plexus.util.IOUtil

class SamePathFilesTransformer implements Transformer {

    def serviceEntires = new MultiValueMap()

    @Override
    boolean canTransformResource(FileTreeElement element) {
        return true;
    }

    @Override
    void transform(String jarName, String path, InputStream is, List<Relocator> relocators) {
        serviceEntires.put(path, new InputStreamWithJarName(is, jarName))
    }

    @Override
    boolean hasTransformedResource() {
        return true;
    }

    @Override
    void modifyOutputStream(ZipOutputStream os) {
        serviceEntires.each { String path, List<InputStreamWithJarName> streams ->
            if (streams.size() == 1) {
                os.putNextEntry(new ZipEntry(path))
                IOUtil.copy(streams[0].is, os)
                os.closeEntry()
            } else {
                streams.each {
                    def newPath = createFilePathWithMergedJarName(path, it.jarName)
                    os.putNextEntry(new ZipEntry(newPath))
                    IOUtil.copy(it.is, os)
                    os.closeEntry()
                }
            }
        }
    }

    private String createFilePathWithMergedJarName(String path, String mergedJarName) {
        def extension = FilenameUtils.getExtension(path)
        def pathWithoutExtension = path
        if (StringUtils.isNotEmpty(extension)) {
            pathWithoutExtension = path.substring(0, path.length() - extension.length() - 1)
            extension = ".$extension"
        }

        def mergedJarExtension = FilenameUtils.getExtension(mergedJarName)
        if (StringUtils.isNotEmpty(mergedJarExtension)) {
            mergedJarName = mergedJarName.substring(0, mergedJarName.length() - mergedJarExtension.length() - 1)
        }

        return "${pathWithoutExtension}_$mergedJarName$extension"
    }

    private class InputStreamWithJarName {

        private InputStream is;
        private String jarName;

        InputStreamWithJarName(InputStream is, String jarName) {
            this.is = is
            this.jarName = jarName
        }
    }
}
