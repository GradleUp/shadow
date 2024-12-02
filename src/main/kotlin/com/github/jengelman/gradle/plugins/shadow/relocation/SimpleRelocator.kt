package com.github.jengelman.gradle.plugins.shadow.relocation

import java.util.regex.Pattern
import org.codehaus.plexus.util.SelectorUtils

/**
 * Modified from [org.apache.maven.plugins.shade.relocation.SimpleRelocator.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/relocation/SimpleRelocator.java).
 *
 * @author Jason van Zyl
 * @author Mauro Talevi
 * @author John Engelman
 */
@CacheableRelocator
public open class SimpleRelocator @JvmOverloads constructor(
  pattern: String? = null,
  shadedPattern: String? = null,
  includes: List<String>? = null,
  excludes: List<String>? = null,
  private val rawString: Boolean = false,
) : Relocator {
  private val pattern: String
  private val pathPattern: String
  private val shadedPattern: String
  private val shadedPathPattern: String
  private val includes = mutableSetOf<String>()
  private val excludes = mutableSetOf<String>()

  init {
    if (rawString) {
      this.pathPattern = pattern.orEmpty()
      this.shadedPathPattern = shadedPattern.orEmpty()
      this.pattern = "" // not used for raw string relocator
      this.shadedPattern = "" // not used for raw string relocator
    } else {
      if (pattern == null) {
        this.pattern = ""
        this.pathPattern = ""
      } else {
        this.pattern = pattern.replace('/', '.')
        this.pathPattern = pattern.replace('.', '/')
      }
      if (shadedPattern == null) {
        this.shadedPattern = "hidden.${this.pattern}"
        this.shadedPathPattern = "hidden/${this.pathPattern}"
      } else {
        this.shadedPattern = shadedPattern.replace('/', '.')
        this.shadedPathPattern = shadedPattern.replace('.', '/')
      }
    }
    this.includes += normalizePatterns(includes)
    this.excludes += normalizePatterns(excludes)
  }

  public open fun include(pattern: String): SimpleRelocator = apply {
    includes += normalizePatterns(listOf(pattern))
  }

  public open fun exclude(pattern: String): SimpleRelocator = apply {
    excludes += normalizePatterns(listOf(pattern))
  }

  override fun canRelocatePath(path: String): Boolean {
    if (rawString) return Pattern.compile(pathPattern).matcher(path).find()
    // If string is too short - no need to perform expensive string operations
    if (path.length < pathPattern.length) return false
    val adjustedPath = if (path.endsWith(".class")) {
      // Safeguard against strings containing only ".class"
      if (path.length == 6) return false
      path.dropLast(6)
    } else {
      path
    }
    // Allow for annoying option of an extra / on the front of a path. See MSHADE-119 comes from getClass().getResource("/a/b/c.properties").
    val startIndex = if (adjustedPath.startsWith("/")) 1 else 0
    val pathStartsWithPattern = adjustedPath.startsWith(pathPattern, startIndex)
    return pathStartsWithPattern && isIncluded(adjustedPath) && !isExcluded(adjustedPath)
  }

  override fun canRelocateClass(className: String): Boolean {
    return !rawString && !className.contains('/') && canRelocatePath(className.replace('.', '/'))
  }

  override fun relocatePath(context: RelocatePathContext): String {
    val path = context.path
    context.stats.relocate(pathPattern, shadedPathPattern)
    return if (rawString) {
      path.replace(pathPattern.toRegex(), shadedPathPattern)
    } else {
      path.replaceFirst(pathPattern, shadedPathPattern)
    }
  }

  override fun relocateClass(context: RelocateClassContext): String {
    context.stats.relocate(pathPattern, shadedPathPattern)
    return context.className.replaceFirst(pattern, shadedPattern)
  }

  override fun applyToSourceContent(sourceContent: String): String {
    return if (rawString) {
      sourceContent
    } else {
      sourceContent.replace("\\b$pattern".toRegex(), shadedPattern)
    }
  }

  private fun isIncluded(path: String): Boolean {
    return includes.isEmpty() || includes.any { SelectorUtils.matchPath(it, path, "/", true) }
  }

  private fun isExcluded(path: String): Boolean {
    return excludes.any { SelectorUtils.matchPath(it, path, "/", true) }
  }

  private companion object {
    fun normalizePatterns(patterns: Collection<String>?) = buildSet {
      patterns ?: return@buildSet
      for (pattern in patterns) {
        // Regex patterns don't need to be normalized and stay as is
        if (pattern.startsWith(SelectorUtils.REGEX_HANDLER_PREFIX)) {
          add(pattern)
          continue
        }

        val classPattern = pattern.replace('.', '/')
        add(classPattern)

        if (classPattern.endsWith("/*")) {
          val packagePattern = classPattern.substring(0, classPattern.lastIndexOf('/'))
          add(packagePattern)
        }
      }
    }
  }
}
