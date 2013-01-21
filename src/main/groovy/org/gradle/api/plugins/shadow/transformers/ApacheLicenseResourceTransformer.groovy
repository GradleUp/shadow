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
package org.gradle.api.plugins.shadow.transformers

import org.gradle.api.file.FileTreeElement
import org.gradle.api.plugins.shadow.relocation.Relocator

import java.util.jar.JarOutputStream

/**
 * Prevents duplicate copies of the license
 *
 * Modified from org.apache.maven.plugins.shade.resouce.ApacheLicenseResourceTransformer.java
 *
 * Modifications
 * @author John Engelman
 */
public class ApacheLicenseResourceTransformer implements Transformer {

    private static final String LICENSE_PATH = "META-INF/LICENSE"

    private static final String LICENSE_TXT_PATH = "META-INF/LICENSE.txt"

    public boolean canTransformResource(FileTreeElement entry) {
        return LICENSE_PATH.equalsIgnoreCase(entry.relativePath.pathString) ||
                LICENSE_TXT_PATH.regionMatches(true, 0, entry.relativePath.pathString, 0, LICENSE_TXT_PATH.length())
    }

    public void transform(FileTreeElement entry, InputStream is, List<Relocator> relocators) {

    }

    public boolean hasTransformedResource() {
        return false
    }

    public void modifyOutputStream(JarOutputStream os) {
    }
}
