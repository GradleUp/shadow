package com.github.jengelman.gradle.plugins.shadow.relocation

import java.util.regex.Pattern
import org.codehaus.plexus.util.SelectorUtils
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

/**
 * Modified from [org.apache.maven.plugins.shade.relocation.SimpleRelocator.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/relocation/SimpleRelocator.java).
 *
 * @author Jason van Zyl
 * @author Mauro Talevi
 * @author John Engelman
 */
@CacheableRelocator
public open class SimpleRelocator @JvmOverloads constructor(
  pattern: String?,
  shadedPattern: String?,
  includes: List<String>? = null,
  excludes: List<String>? = null,
  includeSources: List<String>? = null,
  excludeSources: List<String>? = null,
  private val _rawString: Boolean = false,
) : Relocator {
  private val _pattern: String
  private val _pathPattern: String
  private val _shadedPattern: String
  private val _shadedPathPattern: String
  private val _includes = mutableSetOf<String>()
  private val _excludes = mutableSetOf<String>()
  private val _includeSources = mutableSetOf<String>()
  private val _excludeSources = mutableSetOf<String>()

  init {
    if (_rawString) {
      _pathPattern = pattern.orEmpty()
      _shadedPathPattern = shadedPattern.orEmpty()
      _pattern = "" // not used for raw string relocator
      _shadedPattern = "" // not used for raw string relocator
    } else {
      if (pattern == null) {
        _pattern = ""
        _pathPattern = ""
      } else {
        _pattern = pattern.replace('/', '.')
        _pathPattern = pattern.replace('.', '/')
      }
      if (shadedPattern == null) {
        _shadedPattern = "hidden.${_pattern}"
        _shadedPathPattern = "hidden/$_pathPattern"
      } else {
        _shadedPattern = shadedPattern.replace('/', '.')
        _shadedPathPattern = shadedPattern.replace('.', '/')
      }
    }
    _includes += normalizePatterns(includes)
    _excludes += normalizePatterns(excludes)
    _includeSources += normalizePatterns(includeSources)
    _excludeSources += normalizePatterns(excludeSources)
  }

  @get:Input
  @get:Optional
  public open val pattern: String get() = _pattern

  @get:Input
  public open val pathPattern: String get() = _pathPattern

  @get:Input
  @get:Optional
  public open val shadedPattern: String get() = _shadedPattern

  @get:Input
  public open val shadedPathPattern: String get() = _shadedPathPattern

  @get:Input
  public open val rawString: Boolean get() = _rawString

  @get:Input
  public open val includes: Set<String> get() = _includes

  @get:Input
  public open val excludes: Set<String> get() = _excludes

  @get:Input
  public open val includeSources: Set<String> get() = _includeSources

  @get:Input
  public open val excludeSources: Set<String> get() = _excludeSources

  public open fun include(pattern: String): SimpleRelocator = apply {
    _includes += normalizePatterns(listOf(pattern))
  }

  public open fun exclude(pattern: String): SimpleRelocator = apply {
    _excludes += normalizePatterns(listOf(pattern))
  }

  public open fun includeSources(pattern: String): SimpleRelocator = apply {
    _includeSources += normalizePatterns(listOf(pattern))
  }

  public open fun excludeSources(pattern: String): SimpleRelocator = apply {
    _excludeSources += normalizePatterns(listOf(pattern))
  }

  override fun canRelocatePath(path: String): Boolean {
    if (_rawString) return Pattern.compile(_pathPattern).matcher(path).find()
    // If string is too short - no need to perform expensive string operations
    if (path.length < _pathPattern.length) return false
    val adjustedPath = if (path.endsWith(CLASS_SUFFIX)) {
      // Safeguard against strings containing only ".class"
      if (path.length == CLASS_SUFFIX_LENGTH) return false
      path.dropLast(CLASS_SUFFIX_LENGTH)
    } else {
      path
    }
    // Allow for annoying option of an extra / on the front of a path. See MSHADE-119 comes from getClass().getResource("/a/b/c.properties").
    val startIndex = if (adjustedPath.startsWith("/")) 1 else 0
    val pathStartsWithPattern = adjustedPath.startsWith(_pathPattern, startIndex)
    return pathStartsWithPattern && isIncluded(adjustedPath) && !isExcluded(adjustedPath)
  }

  override fun canRelocateClass(className: String): Boolean {
    return !_rawString && !className.contains('/') && canRelocatePath(className.replace('.', '/'))
  }

  override fun relocatePath(context: RelocatePathContext): String {
    val path = context.path
    context.stats.relocate(_pathPattern, _shadedPathPattern)
    return if (_rawString) {
      path.replace(_pathPattern.toRegex(), _shadedPathPattern)
    } else {
      path.replaceFirst(_pathPattern, _shadedPathPattern)
    }
  }

  override fun relocateClass(context: RelocateClassContext): String {
    context.stats.relocate(_pathPattern, _shadedPathPattern)
    return context.className.replaceFirst(_pattern, _shadedPattern)
  }

  override fun applyToSourceContent(sourceContent: String): String {
    return if (_rawString) {
      sourceContent
    } else {
      sourceContent.replace("\\b$_pattern".toRegex(), _shadedPattern)
    }
  }

  override fun canRelocateSourceFile(sourceFilePath: String): Boolean {
    var tempPath = sourceFilePath
    if (tempPath.endsWith(CLASS_SUFFIX)) {
      // Safeguard against strings containing only ".class"
      if (tempPath.length == CLASS_SUFFIX_LENGTH) {
        return false
      }
      tempPath = tempPath.substring(0, tempPath.length - CLASS_SUFFIX_LENGTH)
    }

    return this.isSourceIncluded(tempPath) && !this.isSourceExcluded(tempPath)
  }

  private fun isIncluded(path: String): Boolean {
    return _includes.isEmpty() || _includes.any { SelectorUtils.matchPath(it, path, "/", true) }
  }

  private fun isExcluded(path: String): Boolean {
    return _excludes.any { SelectorUtils.matchPath(it, path, "/", true) }
  }

  private fun isSourceIncluded(path: String): Boolean {
    return _includeSources.isEmpty() || _includeSources.any { SelectorUtils.matchPath(it, path, "/", true) }
  }

  private fun isSourceExcluded(path: String): Boolean {
    return _excludeSources.any { SelectorUtils.matchPath(it, path, "/", true) }
  }

  private companion object {
    private const val CLASS_SUFFIX = ".class"
    private const val CLASS_SUFFIX_LENGTH = CLASS_SUFFIX.length

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
