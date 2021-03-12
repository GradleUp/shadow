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
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
/**
 * A resource processor that allows the addition of an arbitrary file
 * content into the shaded JAR.
 * <p>
 * Modified from org.apache.maven.plugins.shade.resource.IncludeResourceTransformer.java
 *
 * @author John Engelman
 */
class IncludeResourceTransformer implements Transformer {
    File file

    @Optional
    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    File getFile() {
        file?.exists() ? file : null
    }

    @Optional
    @Input
    String resource

    boolean canTransformResource(FileTreeElement element) {
        return false
    }

    void transform(TransformerContext context) {
        // no op
    }

    boolean hasTransformedResource() {
        return file != null ? file.exists() : false
    }

    void modifyOutputStream(ZipOutputStream os, boolean preserveFileTimestamps) {
        ZipEntry entry = new ZipEntry(resource)
        entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
        os.putNextEntry(entry)

        InputStream is = new FileInputStream(file)
        IOUtil.copy(is, os)
        is.close()
    }
}
