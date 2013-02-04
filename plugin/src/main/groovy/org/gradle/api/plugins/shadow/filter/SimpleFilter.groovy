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

package org.gradle.api.plugins.shadow.filter

import org.gradle.mvn3.org.codehaus.plexus.util.SelectorUtils

/**
 * @author David Blevins
 *
 * Modified from org.apache.maven.plugins.shade.filter.SimpleFilter.java
 *
 * Modifications
 * @author John Engelman
 */
class SimpleFilter implements Filter {

    private List<File> jars

    private List<String> includes

    private List<String> excludes

    SimpleFilter(List<File> jars, List<String> includes, List<String> excludes) {
        this.jars = (jars != null) ? new ArrayList<File>(jars) : []
        this.includes = normalizePatterns(includes)
        this.excludes = normalizePatterns(excludes)
    }

    boolean canFilter(File jar) {
        return jars.contains(jar)
    }

    boolean isFiltered(String classFile) {
        String path = normalizePath(classFile)

        return !(isIncluded(path) && !isExcluded(path))
    }

    boolean isSpecificallyIncluded(String classFile) {
        if (includes == null || includes.isEmpty()) {
            return false
        }

        String path = normalizePath(classFile)

        return isIncluded(path)
    }

    private boolean isIncluded(String classFile) {
        if (includes == null || includes.isEmpty()) {
            return true
        }

        return matchPaths(includes, classFile)
    }

    private boolean isExcluded(String classFile) {
        if (excludes == null || excludes.isEmpty()) {
            return false
        }

        return matchPaths(excludes, classFile)
    }

    private boolean matchPaths(List<String> patterns, String classFile) {
        for (String pattern : patterns) {

            if (SelectorUtils.matchPath(pattern, classFile)) {
                return true
            }
        }

        return false
    }

    private String normalizePath(String path) {
        return (path != null) ? path.replace(File.separatorChar == '/' ? '\\' : '/', File.separatorChar as String) : null
    }

    private List<String> normalizePatterns(List<String> patterns) {
        List<String> result = []

        if (patterns != null) {
            for (String pattern : patterns) {
                pattern = normalizePath(pattern)

                if (pattern.endsWith(File.separator)) {
                    pattern += "**"
                }

                result.add(pattern)
            }
        }

        return result
    }

    void finished() {
    }

    String toString() {
        "$jars : $includes : $excludes"
    }
}
