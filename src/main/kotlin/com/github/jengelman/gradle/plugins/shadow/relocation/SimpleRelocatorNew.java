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
package com.github.jengelman.gradle.plugins.shadow.relocation;

import org.codehaus.plexus.util.SelectorUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author Jason van Zyl
 * @author Mauro Talevi
 */
public class SimpleRelocatorNew implements Relocator {
    /**
     * Match dot, slash or space at end of string
     */
    private static final Pattern RX_ENDS_WITH_DOT_SLASH_SPACE = Pattern.compile("[./ ]$");

    /**
     * Match <ul>
     *     <li>certain Java keywords + space</li>
     *     <li>beginning of Javadoc link + optional line breaks and continuations with '*'</li>
     *     <li>(opening curly brace / opening parenthesis / comma / equals / semicolon) + space</li>
     *     <li>(closing curly brace / closing multi-line comment) + space</li>
     * </ul>
     * at end of string
     */
    private static final Pattern RX_ENDS_WITH_JAVA_KEYWORD = Pattern.compile(
            "\\b(import|package|public|protected|private|static|final|synchronized|abstract|volatile|extends|implements|throws) $"
                    + "|"
                    + "\\{@link( \\*)* $"
                    + "|"
                    + "([{}(=;,]|\\*/) $");

    private final String pattern;

    private final String pathPattern;

    private final String shadedPattern;

    private final String shadedPathPattern;

    private final Set<String> includes;

    private final Set<String> excludes;

    private final Set<String> sourcePackageExcludes = new LinkedHashSet<>();

    private final Set<String> sourcePathExcludes = new LinkedHashSet<>();

    private final boolean rawString;

    public SimpleRelocatorNew(String patt, String shadedPattern, List<String> includes, List<String> excludes) {
        this(patt, shadedPattern, includes, excludes, false);
    }

    public SimpleRelocatorNew(
            String patt, String shadedPattern, List<String> includes, List<String> excludes, boolean rawString) {
        this.rawString = rawString;

        if (rawString) {
            this.pathPattern = patt;
            this.shadedPathPattern = shadedPattern;

            this.pattern = null; // not used for raw string relocator
            this.shadedPattern = null; // not used for raw string relocator
        } else {
            if (patt == null) {
                this.pattern = "";
                this.pathPattern = "";
            } else {
                this.pattern = patt.replace('/', '.');
                this.pathPattern = patt.replace('.', '/');
            }

            if (shadedPattern != null) {
                this.shadedPattern = shadedPattern.replace('/', '.');
                this.shadedPathPattern = shadedPattern.replace('.', '/');
            } else {
                this.shadedPattern = "hidden." + this.pattern;
                this.shadedPathPattern = "hidden/" + this.pathPattern;
            }
        }

        this.includes = normalizePatterns(includes);
        this.excludes = normalizePatterns(excludes);

        // Don't replace all dots to slashes, otherwise /META-INF/maven/${groupId} can't be matched.
        if (includes != null && !includes.isEmpty()) {
            this.includes.addAll(includes);
        }

        if (excludes != null && !excludes.isEmpty()) {
            this.excludes.addAll(excludes);
        }

        if (!rawString && this.excludes != null) {
            // Create exclude pattern sets for sources
            for (String exclude : this.excludes) {
                // Excludes should be subpackages of the global pattern
                if (exclude.startsWith(pattern)) {
                    sourcePackageExcludes.add(
                            exclude.substring(pattern.length()).replaceFirst("[.][*]$", ""));
                }
                // Excludes should be subpackages of the global pattern
                if (exclude.startsWith(pathPattern)) {
                    sourcePathExcludes.add(
                            exclude.substring(pathPattern.length()).replaceFirst("[/][*]$", ""));
                }
            }
        }
    }

    private static Set<String> normalizePatterns(Collection<String> patterns) {
        Set<String> normalized = null;

        if (patterns != null && !patterns.isEmpty()) {
            normalized = new LinkedHashSet<>();
            for (String pattern : patterns) {
                String classPattern = pattern.replace('.', '/');
                normalized.add(classPattern);
                // Actually, class patterns should just use 'foo.bar.*' ending with a single asterisk, but some users
                // mistake them for path patterns like 'my/path/**', so let us be a bit more lenient here.
                if (classPattern.endsWith("/*") || classPattern.endsWith("/**")) {
                    String packagePattern = classPattern.substring(0, classPattern.lastIndexOf('/'));
                    normalized.add(packagePattern);
                }
            }
        }

        return normalized;
    }

    private boolean isIncluded(String path) {
        if (includes != null && !includes.isEmpty()) {
            for (String include : includes) {
                if (SelectorUtils.matchPath(include, path, true)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private boolean isExcluded(String path) {
        if (excludes != null && !excludes.isEmpty()) {
            for (String exclude : excludes) {
                if (SelectorUtils.matchPath(exclude, path, true)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean canRelocatePath(String path) {
        if (rawString) {
            return Pattern.compile(pathPattern).matcher(path).find();
        }

        if (path.endsWith(".class")) {
            path = path.substring(0, path.length() - 6);
        }

        // Allow for annoying option of an extra / on the front of a path. See MSHADE-119; comes from
        // getClass().getResource("/a/b/c.properties").
        if (!path.isEmpty() && path.charAt(0) == '/') {
            path = path.substring(1);
        }

        return isIncluded(path) && !isExcluded(path) && path.startsWith(pathPattern);
    }

    @Override
    public boolean canRelocateClass(String clazz) {
        return !rawString && clazz.indexOf('/') < 0 && canRelocatePath(clazz.replace('.', '/'));
    }

    @Override
    public String relocatePath(String path) {
        if (rawString) {
            return path.replaceAll(pathPattern, shadedPathPattern);
        } else {
            return path.replaceFirst(pathPattern, shadedPathPattern);
        }
    }

    @Override
    public String relocateClass(String clazz) {
        return rawString ? clazz : clazz.replaceFirst(pattern, shadedPattern);
    }

    @Override
    public String applyToSourceContent(String sourceContent) {
        if (rawString) {
            return sourceContent;
        }
        sourceContent = shadeSourceWithExcludes(sourceContent, pattern, shadedPattern, sourcePackageExcludes);
        return shadeSourceWithExcludes(sourceContent, pathPattern, shadedPathPattern, sourcePathExcludes);
    }

    private String shadeSourceWithExcludes(
            String sourceContent, String patternFrom, String patternTo, Set<String> excludedPatterns) {
        // Usually shading makes package names a bit longer, so make buffer 10% bigger than original source
        StringBuilder shadedSourceContent = new StringBuilder(sourceContent.length() * 11 / 10);
        boolean isFirstSnippet = true;
        // Make sure that search pattern starts at word boundary and that we look for literal ".", not regex jokers
        String[] snippets = sourceContent.split("\\b" + patternFrom.replace(".", "[.]") + "\\b");
        for (int i = 0, snippetsLength = snippets.length; i < snippetsLength; i++) {
            String snippet = snippets[i];
            String previousSnippet = isFirstSnippet ? "" : snippets[i - 1];
            boolean doExclude = false;
            for (String excludedPattern : excludedPatterns) {
                if (snippet.startsWith(excludedPattern)) {
                    doExclude = true;
                    break;
                }
            }
            if (isFirstSnippet) {
                shadedSourceContent.append(snippet);
                isFirstSnippet = false;
            } else {
                String previousSnippetOneLine = previousSnippet.replaceAll("\\s+", " ");
                boolean afterDotSlashSpace = RX_ENDS_WITH_DOT_SLASH_SPACE
                        .matcher(previousSnippetOneLine)
                        .find();
                boolean afterJavaKeyWord = RX_ENDS_WITH_JAVA_KEYWORD
                        .matcher(previousSnippetOneLine)
                        .find();
                boolean shouldExclude = doExclude || afterDotSlashSpace && !afterJavaKeyWord;
                shadedSourceContent
                        .append(shouldExclude ? patternFrom : patternTo)
                        .append(snippet);
            }
        }
        return shadedSourceContent.toString();
    }
}
