package com.github.jengelman.gradle.plugins.shadow.relocation

import java.util.Objects
import java.util.regex.Pattern
import org.codehaus.plexus.util.SelectorUtils
import org.gradle.api.tasks.Input

/**
 * Modified from
 * [org.apache.maven.plugins.shade.relocation.SimpleRelocator.java](https://github.com/apache/maven-shade-plugin/blob/master/src/main/java/org/apache/maven/plugins/shade/relocation/SimpleRelocator.java).
 *
 * @author Jason van Zyl
 * @author Mauro Talevi
 * @author John Engelman
 */
@CacheableRelocator
public open class SimpleRelocator
@JvmOverloads
constructor(
  pattern: String? = null,
  shadedPattern: String? = null,
  includes: List<String>? = null,
  excludes: List<String>? = null,
  @get:Input internal val rawString: Boolean = false,
  @get:Input override var skipStringConstants: Boolean = false,
) : Relocator {
  @get:Input internal val pattern: String
  @get:Input internal val pathPattern: String
  @get:Input internal val shadedPattern: String
  @get:Input internal val shadedPathPattern: String
  private val sourcePackageExcludes = mutableSetOf<String>()
  private val sourcePathExcludes = mutableSetOf<String>()

  @get:Input public val includes: MutableSet<String> = mutableSetOf()
  @get:Input public val excludes: MutableSet<String> = mutableSetOf()

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
  }

  public open fun include(pattern: String) {
    includes.addAll(normalizePatterns(listOf(pattern)))
    includes.add(pattern)
  }

  public open fun exclude(pattern: String) {
    excludes.addAll(normalizePatterns(listOf(pattern)))
    excludes.add(pattern)
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
    return isIncluded(adjustedPath) &&
      !isExcluded(adjustedPath) &&
      adjustedPath.startsWith(pathPattern)
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

  override fun applyToSourceContent(sourceContent: String): String {
    if (rawString || pattern.isEmpty()) return sourceContent
    val sourceIncludes = getSourceSubpatterns(includes, pattern)
    val sourceExcludes = getSourceSubpatterns(excludes, pattern)
    val content =
      shadeSource(
        sourceContent = sourceContent,
        patternFrom = pattern,
        patternTo = shadedPattern,
        includedPatterns = sourceIncludes,
        hasIncludes = includes.isNotEmpty(),
        excludedPatterns = sourceExcludes,
      )
    return shadeSource(
      sourceContent = content,
      patternFrom = pathPattern,
      patternTo = shadedPathPattern,
      includedPatterns = sourceIncludes,
      hasIncludes = includes.isNotEmpty(),
      excludedPatterns = sourceExcludes,
    )
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SimpleRelocator) return false
    return rawString == other.rawString &&
      skipStringConstants == other.skipStringConstants &&
      pattern == other.pattern &&
      pathPattern == other.pathPattern &&
      shadedPattern == other.shadedPattern &&
      shadedPathPattern == other.shadedPathPattern &&
      includes == other.includes &&
      excludes == other.excludes
  }

  override fun hashCode(): Int =
    Objects.hash(
      rawString,
      skipStringConstants,
      pattern,
      pathPattern,
      shadedPattern,
      shadedPathPattern,
      includes,
      excludes,
    )

  override fun toString(): String = buildString {
    append("SimpleRelocator(")
    append("rawString=$rawString").append(", ")
    append("skipStringConstants=$skipStringConstants").append(", ")
    append("pattern='$pattern'").append(", ")
    append("pathPattern='$pathPattern'").append(", ")
    append("shadedPattern='$shadedPattern'").append(", ")
    append("shadedPathPattern='$shadedPathPattern'").append(", ")
    append("includes=$includes").append(", ")
    append("excludes=$excludes")
    append(")")
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
        // Regex patterns don't need to be normalized and stay as is.
        if (pattern.startsWith(SelectorUtils.REGEX_HANDLER_PREFIX)) {
          add(pattern)
          continue
        }

        val separatorIndex = pattern.indexOfLast { it == '/' || it == '\\' }
        val fileName = pattern.substring(separatorIndex + 1)
        val fileParent = pattern.substring(0, separatorIndex.coerceAtLeast(0))
        val filePattern =
          if (fileParent.isNotEmpty() && fileName.isNotEmpty()) {
            // It's a file pattern like `kotlin/kotlin.kotlin_builtins`, so we don't need to
            // normalize it.
            pattern
          } else {
            pattern.replace('.', '/')
          }
        add(filePattern)
        // Actually, class patterns should just use 'foo.bar.*' ending with a single asterisk,
        // but some users mistake them for path patterns like 'my/path/**', so let us be a bit more
        // lenient here.
        if (filePattern.endsWith("/*") || filePattern.endsWith("/**")) {
          val packagePattern = filePattern.take(filePattern.lastIndexOf('/'))
          add(packagePattern)
        }
      }
    }

    fun shadeSource(
      sourceContent: String,
      patternFrom: String,
      patternTo: String,
      includedPatterns: Set<String>,
      hasIncludes: Boolean,
      excludedPatterns: Set<String>,
    ): String {
      if (hasIncludes && includedPatterns.isEmpty()) {
        return sourceContent
      }

      val regex = Regex("\\b" + Regex.escape(patternFrom) + "\\b")
      val matches = regex.findAll(sourceContent).toList()
      if (matches.isEmpty()) return sourceContent

      val result = StringBuilder((sourceContent.length * 1.1).toInt())
      var lastIndex = 0

      for (match in matches) {
        val matchStart = match.range.first
        val matchEnd = match.range.last + 1

        result.append(sourceContent, lastIndex, matchStart)
        lastIndex = matchEnd

        val isIncluded =
          !hasIncludes || includedPatterns.any { matchesSubpattern(sourceContent, matchEnd, it) }
        val isExcluded = excludedPatterns.any { matchesSubpattern(sourceContent, matchEnd, it) }
        val contextValid = isSourceContextValid(patternFrom, sourceContent, matchStart, matchEnd)

        if (isIncluded && !isExcluded && contextValid) {
          result.append(patternTo)
        } else {
          result.append(patternFrom)
        }
      }

      result.append(sourceContent, lastIndex, sourceContent.length)
      return result.toString()
    }

    fun getSourceSubpatterns(patterns: Set<String>, patternPrefix: String): Set<String> {
      if (patternPrefix.isEmpty()) return emptySet()
      val result = mutableSetOf<String>()
      val dotPrefix = patternPrefix.replace('/', '.')
      val slashPrefix = patternPrefix.replace('.', '/')
      val trailingWildcardRegex = "[./][*]+$".toRegex()

      for (pat in patterns) {
        val dotPat = pat.replace('/', '.')
        if (dotPat.startsWith(dotPrefix)) {
          val sub = dotPat.substring(dotPrefix.length).replaceFirst(trailingWildcardRegex, "")
          if (sub.isEmpty()) {
            result.add("")
          } else {
            result.add(sub)
            result.add(sub.replace('.', '/'))
          }
        }
        val slashPat = pat.replace('.', '/')
        if (slashPat.startsWith(slashPrefix)) {
          val sub = slashPat.substring(slashPrefix.length).replaceFirst(trailingWildcardRegex, "")
          if (sub.isEmpty()) {
            result.add("")
          } else {
            result.add(sub)
            result.add(sub.replace('/', '.'))
          }
        }
      }
      return result
    }

    fun matchesSubpattern(
      content: CharSequence,
      offset: Int = 0,
      subpattern: String,
    ): Boolean {
      val subLen = subpattern.length
      if (offset + subLen > content.length) return false
      for (i in 0 until subLen) {
        if (content[offset + i] != subpattern[i]) return false
      }
      if (subLen == 0 || offset + subLen == content.length) return true
      if (subpattern.endsWith('.') || subpattern.endsWith('/')) return true
      val nextChar = content[offset + subLen]
      return !nextChar.isLetterOrDigit() && nextChar != '_'
    }

    fun isSourceContextValid(
      pattern: String,
      sourceContent: CharSequence,
      matchStart: Int,
      matchEnd: Int,
    ): Boolean {
      var prevIndex = matchStart - 1
      while (prevIndex >= 0 && sourceContent[prevIndex].isWhitespace()) {
        prevIndex--
      }

      if (prevIndex >= 0) {
        val prevChar = sourceContent[prevIndex]
        if (prevChar == '.') {
          val beforeDot = if (prevIndex > 0) sourceContent[prevIndex - 1] else null
          // A dot is only a package separator if it's not a Kotlin range '..' or varargs/spread
          // '...'
          if (beforeDot != '.') {
            return false
          }
        }
        if (prevChar == '/') {
          val beforeSlash = if (prevIndex > 0) sourceContent[prevIndex - 1] else null
          // Only reject if pattern does not contain '.' and the slash is an actual path delimiter
          // (not closing a block comment '*/' or a single-line comment '//')
          if (!pattern.contains('.') && beforeSlash != '*' && beforeSlash != '/') {
            return false
          }
        }
      }

      // In all JVM languages, qualified names containing '.' or '/' cannot be local identifiers.
      if (pattern.contains('.') || pattern.contains('/')) {
        return true
      }

      // For unqualified single-word patterns (e.g. "io", "foo"), check if followed by '.' or '/'
      var nextIndex = matchEnd
      while (nextIndex < sourceContent.length && sourceContent[nextIndex].isWhitespace()) {
        nextIndex++
      }
      if (nextIndex < sourceContent.length) {
        val nextChar = sourceContent[nextIndex]
        if (nextChar == '.' || nextChar == '/') {
          return true
        }
      }

      // Check if preceded by 'package', 'import', or '{@link'
      if (prevIndex >= 0) {
        var tokenStart = prevIndex
        while (tokenStart > 0 && sourceContent[tokenStart - 1].isJavaIdentifierPart()) {
          tokenStart--
        }
        val prevToken = sourceContent.subSequence(tokenStart, prevIndex + 1).toString()
        if (prevToken == "package" || prevToken == "import") {
          return true
        }
        val lookbackStart = (matchStart - 32).coerceAtLeast(0)
        val lookbackSnippet = sourceContent.substring(lookbackStart, matchStart)
        if (lookbackSnippet.contains("{@link")) {
          return true
        }
      }

      return false
    }
  }
}
