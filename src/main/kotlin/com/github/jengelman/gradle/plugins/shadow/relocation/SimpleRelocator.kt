package com.github.jengelman.gradle.plugins.shadow.relocation

import java.util.regex.Pattern
import org.codehaus.plexus.util.SelectorUtils
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input

/**
 * Modified from [org.apache.maven.plugins.shade.relocation.SimpleRelocator.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/relocation/SimpleRelocator.java).
 *
 * @author Jason van Zyl
 * @author Mauro Talevi
 * @author John Engelman
 */
@CacheableRelocator
public open class SimpleRelocator @JvmOverloads constructor(
  objectFactory: ObjectFactory,
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
  private val sourcePackageExcludes = mutableSetOf<String>()
  private val sourcePathExcludes = mutableSetOf<String>()

  @get:Input
  public val includes: MutableSet<String> = mutableSetOf()

  @get:Input
  public val excludes: MutableSet<String> = mutableSetOf()

  init {
    if (rawString) {
      this.pathPattern = pattern.orEmpty()
      this.shadedPathPattern = shadedPattern.orEmpty()
      this.pattern = "" // Not used for raw string relocator.
      this.shadedPattern = "" // Not used for raw string relocator.
    } else {
      if (pattern == null) {
        this.pattern = ""
        this.pathPattern = ""
      } else {
        this.pattern = pattern.replace('/', '.')
        this.pathPattern = pattern.replace('.', '/')
      }
      if (shadedPattern != null) {
        this.shadedPattern = shadedPattern.replace('/', '.')
        this.shadedPathPattern = shadedPattern.replace('.', '/')
      } else {
        this.shadedPattern = "hidden.${this.pattern}"
        this.shadedPathPattern = "hidden/${this.pathPattern}"
      }
    }
    this.includes.addAll(normalizePatterns(includes))
    this.excludes.addAll(normalizePatterns(excludes))

    // Don't replace all dots to slashes, otherwise /META-INF/maven/${groupId} can't be matched.
    if (!includes.isNullOrEmpty()) {
      this.includes.addAll(includes)
    }
    if (!excludes.isNullOrEmpty()) {
      this.excludes.addAll(excludes)
    }

    if (!rawString) {
      // Create exclude pattern sets for sources.
      for (exclude in this.excludes) {
        // Excludes should be subpackages of the global pattern.
        if (exclude.startsWith(this.pattern)) {
          sourcePackageExcludes.add(
            exclude.substring(this.pattern.length).replaceFirst("[.][*]$".toRegex(), ""),
          )
        }
        // Excludes should be subpackages of the global pattern.
        if (exclude.startsWith(pathPattern)) {
          sourcePathExcludes.add(
            exclude.substring(pathPattern.length).replaceFirst("/[*]$".toRegex(), ""),
          )
        }
      }
    }
  }

  public open fun include(pattern: String): SimpleRelocator = apply {
    includes.addAll(normalizePatterns(listOf(pattern)))
  }

  public open fun exclude(pattern: String): SimpleRelocator = apply {
    excludes.addAll(normalizePatterns(listOf(pattern)))
  }

  override fun canRelocatePath(path: String): Boolean {
    if (rawString) return Pattern.compile(pathPattern).matcher(path).find()
    // If string is too short - no need to perform expensive string operations.
    if (path.length < pathPattern.length) return false
    var adjustedPath = path.removeSuffix(".class")
    // Safeguard against strings containing only ".class".
    if (adjustedPath.isEmpty()) return false
    // Allow for annoying option of an extra / on the front of a path. See MSHADE-119;
    // comes from getClass().getResource("/a/b/c.properties").
    adjustedPath = adjustedPath.removePrefix("/")
    return isIncluded(adjustedPath) && !isExcluded(adjustedPath) && adjustedPath.startsWith(pathPattern)
  }

  override fun canRelocateClass(className: String): Boolean {
    return !rawString && !className.contains('/') && canRelocatePath(className.replace('.', '/'))
  }

  override fun relocatePath(context: RelocatePathContext): String {
    val path = context.path
    return if (rawString) {
      path.replace(pathPattern.toRegex(), shadedPathPattern)
    } else {
      path.replaceFirst(pathPattern.toRegex(), shadedPathPattern)
    }
  }

  override fun relocateClass(context: RelocateClassContext): String {
    val clazz = context.className
    return if (rawString) clazz else clazz.replaceFirst(pattern.toRegex(), shadedPattern)
  }

  /**
   * We don't call this function now, so we don't have to expose [sourcePackageExcludes] and [sourcePathExcludes] as inputs.
   */
  override fun applyToSourceContent(sourceContent: String): String {
    if (rawString) return sourceContent
    val content = shadeSourceWithExcludes(sourceContent, pattern, shadedPattern, sourcePackageExcludes)
    return shadeSourceWithExcludes(content, pathPattern, shadedPathPattern, sourcePathExcludes)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SimpleRelocator) return false
    return rawString == other.rawString &&
      pattern == other.pattern &&
      pathPattern == other.pathPattern &&
      shadedPattern == other.shadedPattern &&
      shadedPathPattern == other.shadedPathPattern &&
      sourcePackageExcludes == other.sourcePackageExcludes &&
      sourcePathExcludes == other.sourcePathExcludes &&
      includes == other.includes &&
      excludes == other.excludes
  }

  override fun hashCode(): Int {
    var result = rawString.hashCode()
    result = 31 * result + pattern.hashCode()
    result = 31 * result + pathPattern.hashCode()
    result = 31 * result + shadedPattern.hashCode()
    result = 31 * result + shadedPathPattern.hashCode()
    result = 31 * result + sourcePackageExcludes.hashCode()
    result = 31 * result + sourcePathExcludes.hashCode()
    result = 31 * result + includes.hashCode()
    result = 31 * result + excludes.hashCode()
    return result
  }

  private fun isIncluded(path: String): Boolean {
    return includes.isEmpty() || includes.any { SelectorUtils.matchPath(it, path, "/", true) }
  }

  private fun isExcluded(path: String): Boolean {
    return excludes.any { SelectorUtils.matchPath(it, path, "/", true) }
  }

  private companion object {
    /**
     * Match dot, slash or space at end of string
     */
    val RX_ENDS_WITH_DOT_SLASH_SPACE: Pattern = Pattern.compile("[./ ]$")

    /**
     * Match
     *  - certain Java keywords + space
     *  - beginning of Javadoc link + optional line breaks and continuations with '*'
     *  - (opening curly brace / opening parenthesis / comma / equals / semicolon) + space
     *  - (closing curly brace / closing multi-line comment) + space
     *
     * at end of string
     */
    val RX_ENDS_WITH_JAVA_KEYWORD: Pattern = Pattern.compile(
      "\\b(import|package|public|protected|private|static|final|synchronized|abstract|volatile|extends|implements|throws) $" +
        "|" +
        "\\{@link( \\*)* $" +
        "|" +
        "([{}(=;,]|\\*/) $",
    )

    fun normalizePatterns(patterns: Collection<String>?) = buildSet {
      patterns ?: return@buildSet
      for (pattern in patterns) {
        // Regex patterns don't need to be normalized and stay as is.
        if (pattern.startsWith(SelectorUtils.REGEX_HANDLER_PREFIX)) {
          add(pattern)
          continue
        }

        val classPattern = pattern.replace('.', '/')
        add(classPattern)
        // Actually, class patterns should just use 'foo.bar.*' ending with a single asterisk, but some users
        // mistake them for path patterns like 'my/path/**', so let us be a bit more lenient here.
        if (classPattern.endsWith("/*") || classPattern.endsWith("/**")) {
          val packagePattern = classPattern.substring(0, classPattern.lastIndexOf('/'))
          add(packagePattern)
        }
      }
    }

    fun shadeSourceWithExcludes(
      sourceContent: String,
      patternFrom: String,
      patternTo: String,
      excludedPatterns: Set<String>,
    ): String {
      // Usually shading makes package names a bit longer, so make buffer 10% bigger than original source.
      val shadedSourceContent = StringBuilder(sourceContent.length * 11 / 10)
      // Make sure that search pattern starts at word boundary and that we look for literal ".", not regex jokers.
      val snippets = sourceContent.split(("\\b" + patternFrom.replace(".", "[.]") + "\\b").toRegex())
        .filter(CharSequence::isNotEmpty)
      snippets.forEachIndexed { i, snippet ->
        val isFirstSnippet = i == 0
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
        } else {
          val previousSnippetOneLine = previousSnippet.replace("\\s+".toRegex(), " ")
          val afterDotSlashSpace = RX_ENDS_WITH_DOT_SLASH_SPACE.matcher(previousSnippetOneLine).find()
          val afterJavaKeyWord = RX_ENDS_WITH_JAVA_KEYWORD.matcher(previousSnippetOneLine).find()
          val shouldExclude = doExclude || afterDotSlashSpace && !afterJavaKeyWord
          shadedSourceContent
            .append(if (shouldExclude) patternFrom else patternTo)
            .append(snippet)
        }
      }
      return shadedSourceContent.toString()
    }
  }
}
