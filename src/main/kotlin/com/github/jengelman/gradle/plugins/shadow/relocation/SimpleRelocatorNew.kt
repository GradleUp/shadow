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
package com.github.jengelman.gradle.plugins.shadow.relocation

import java.util.regex.Pattern
import org.codehaus.plexus.util.SelectorUtils

/**
 * @author Jason van Zyl
 * @author Mauro Talevi
 */
internal class SimpleRelocatorNew @JvmOverloads constructor(
  patt: String?,
  shadedPattern: String?,
  includes: List<String>?,
  excludes: List<String>?,
  private val rawString: Boolean = false,
) : Relocator {
  private var pattern: String? = null

  private var pathPattern: String? = null

  private var shadedPattern: String? = null

  private var shadedPathPattern: String? = null

  private val includes: MutableSet<String>?

  private val excludes: MutableSet<String>?

  private val sourcePackageExcludes: MutableSet<String> = LinkedHashSet()

  private val sourcePathExcludes: MutableSet<String> = LinkedHashSet()

  init {
    if (rawString) {
      this.pathPattern = patt
      this.shadedPathPattern = shadedPattern

      this.pattern = null // not used for raw string relocator
      this.shadedPattern = null // not used for raw string relocator
    } else {
      if (patt == null) {
        this.pattern = ""
        this.pathPattern = ""
      } else {
        this.pattern = patt.replace('/', '.')
        this.pathPattern = patt.replace('.', '/')
      }

      if (shadedPattern != null) {
        this.shadedPattern = shadedPattern.replace('/', '.')
        this.shadedPathPattern = shadedPattern.replace('.', '/')
      } else {
        this.shadedPattern = "hidden." + this.pattern
        this.shadedPathPattern = "hidden/" + this.pathPattern
      }
    }

    this.includes = normalizePatterns(includes)
    this.excludes = normalizePatterns(excludes)

    // Don't replace all dots to slashes, otherwise /META-INF/maven/${groupId} can't be matched.
    if (!includes.isNullOrEmpty()) {
      this.includes!!.addAll(includes)
    }

    if (!excludes.isNullOrEmpty()) {
      this.excludes!!.addAll(excludes)
    }

    if (!rawString && this.excludes != null) {
      // Create exclude pattern sets for sources
      for (exclude in this.excludes) {
        // Excludes should be subpackages of the global pattern
        if (exclude.startsWith(pattern!!)) {
          sourcePackageExcludes.add(
            exclude.substring(pattern!!.length).replaceFirst("[.][*]$".toRegex(), ""),
          )
        }
        // Excludes should be subpackages of the global pattern
        if (exclude.startsWith(pathPattern!!)) {
          sourcePathExcludes.add(
            exclude.substring(pathPattern!!.length).replaceFirst("/[*]$".toRegex(), ""),
          )
        }
      }
    }
  }

  private fun isIncluded(path: String): Boolean {
    if (!includes.isNullOrEmpty()) {
      for (include in includes) {
        if (SelectorUtils.matchPath(include, path, true)) {
          return true
        }
      }
      return false
    }
    return true
  }

  private fun isExcluded(path: String): Boolean {
    if (!excludes.isNullOrEmpty()) {
      for (exclude in excludes) {
        if (SelectorUtils.matchPath(exclude, path, true)) {
          return true
        }
      }
    }
    return false
  }

  override fun canRelocatePath(path: String): Boolean {
    var newPath = path
    if (rawString) {
      return Pattern.compile(pathPattern!!).matcher(newPath).find()
    }

    if (newPath.endsWith(".class")) {
      newPath = newPath.substring(0, newPath.length - 6)
    }

    // Allow for annoying option of an extra / on the front of a path. See MSHADE-119; comes from
    // getClass().getResource("/a/b/c.properties").
    if (newPath.isNotEmpty() && newPath[0] == '/') {
      newPath = newPath.substring(1)
    }

    return isIncluded(newPath) && !isExcluded(newPath) && newPath.startsWith(pathPattern!!)
  }

  override fun canRelocateClass(className: String): Boolean {
    return !rawString && className.indexOf('/') < 0 && canRelocatePath(className.replace('.', '/'))
  }

  override fun relocatePath(context: RelocatePathContext): String {
    return if (rawString) {
      context.path.replace(pathPattern!!.toRegex(), shadedPathPattern!!)
    } else {
      context.path.replaceFirst(pathPattern!!.toRegex(), shadedPathPattern!!)
    }
  }

  override fun relocateClass(context: RelocateClassContext): String {
    val clazz = context.className
    return if (rawString) clazz else clazz.replaceFirst(pattern!!.toRegex(), shadedPattern!!)
  }

  override fun applyToSourceContent(sourceContent: String): String {
    if (rawString) {
      return sourceContent
    }
    val content = shadeSourceWithExcludes(sourceContent, pattern!!, shadedPattern, sourcePackageExcludes)
    return shadeSourceWithExcludes(content, pathPattern!!, shadedPathPattern, sourcePathExcludes)
  }

  private fun shadeSourceWithExcludes(
    sourceContent: String,
    patternFrom: String,
    patternTo: String?,
    excludedPatterns: Set<String>,
  ): String {
    // Usually shading makes package names a bit longer, so make buffer 10% bigger than original source
    val shadedSourceContent = StringBuilder(sourceContent.length * 11 / 10)
    var isFirstSnippet = true
    // Make sure that search pattern starts at word boundary and that we look for literal ".", not regex jokers
    val snippets = sourceContent.split(("\\b" + patternFrom.replace(".", "[.]") + "\\b").toRegex())
      .dropLastWhile { it.isEmpty() }.toTypedArray()
    var i = 0
    val snippetsLength = snippets.size
    while (i < snippetsLength) {
      val snippet = snippets[i]
      val previousSnippet = if (isFirstSnippet) "" else snippets[i - 1]
      var doExclude = false
      for (excludedPattern in excludedPatterns) {
        if (snippet.startsWith(excludedPattern)) {
          doExclude = true
          break
        }
      }
      if (isFirstSnippet) {
        shadedSourceContent.append(snippet)
        isFirstSnippet = false
      } else {
        val previousSnippetOneLine = previousSnippet.replace("\\s+".toRegex(), " ")
        val afterDotSlashSpace = RX_ENDS_WITH_DOT_SLASH_SPACE
          .matcher(previousSnippetOneLine)
          .find()
        val afterJavaKeyWord = RX_ENDS_WITH_JAVA_KEYWORD
          .matcher(previousSnippetOneLine)
          .find()
        val shouldExclude = doExclude || afterDotSlashSpace && !afterJavaKeyWord
        shadedSourceContent
          .append(if (shouldExclude) patternFrom else patternTo)
          .append(snippet)
      }
      i++
    }
    return shadedSourceContent.toString()
  }

  public companion object {
    /**
     * Match dot, slash or space at end of string
     */
    private val RX_ENDS_WITH_DOT_SLASH_SPACE: Pattern = Pattern.compile("[./ ]$")

    /**
     * Match
     *  * certain Java keywords + space
     *  * beginning of Javadoc link + optional line breaks and continuations with '*'
     *  * (opening curly brace / opening parenthesis / comma / equals / semicolon) + space
     *  * (closing curly brace / closing multi-line comment) + space
     *
     * at end of string
     */
    private val RX_ENDS_WITH_JAVA_KEYWORD: Pattern = Pattern.compile(
      (
        "\\b(import|package|public|protected|private|static|final|synchronized|abstract|volatile|extends|implements|throws) $" +
          "|" +
          "\\{@link( \\*)* $" +
          "|" +
          "([{}(=;,]|\\*/) $"
        ),
    )

    private fun normalizePatterns(patterns: Collection<String>?): MutableSet<String>? {
      var normalized: MutableSet<String>? = null

      if (!patterns.isNullOrEmpty()) {
        normalized = LinkedHashSet()
        for (pattern in patterns) {
          val classPattern = pattern.replace('.', '/')
          normalized.add(classPattern)
          // Actually, class patterns should just use 'foo.bar.*' ending with a single asterisk, but some users
          // mistake them for path patterns like 'my/path/**', so let us be a bit more lenient here.
          if (classPattern.endsWith("/*") || classPattern.endsWith("/**")) {
            val packagePattern = classPattern.substring(0, classPattern.lastIndexOf('/'))
            normalized.add(packagePattern)
          }
        }
      }

      return normalized
    }
  }
}
