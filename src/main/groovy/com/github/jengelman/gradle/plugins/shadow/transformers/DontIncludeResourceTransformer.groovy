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

import org.apache.tools.zip.ZipOutputStream
import org.codehaus.plexus.util.StringUtils
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

/**
 * A resource processor that prevents the inclusion of an arbitrary
 * resource into the shaded JAR.
 * <p>
 * Modified from org.apache.maven.plugins.shade.resource.DontIncludeResourceTransformer.java
 *
 * @author John Engelman
 */
class DontIncludeResourceTransformer implements Transformer {

    @Optional
    @Input
    String resource

    @Override
    boolean canTransformResource(FileTreeElement element) {
        def path = element.relativePath.pathString
        if (StringUtils.isNotEmpty(resource) && path.endsWith(resource)) {
            return true
        }

        return false
    }

    @Override
    void transform(TransformerContext context) {
        // no op
    }

    @Override
    boolean hasTransformedResource() {
        return false
    }

    @Override
    void modifyOutputStream(ZipOutputStream os, boolean preserveFileTimestamps) {
        // no op
    }
}
