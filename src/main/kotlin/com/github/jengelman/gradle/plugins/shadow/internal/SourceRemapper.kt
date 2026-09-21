package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocatePathContext
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocatePath

/**
 * Remaps source content by applying relocators in a single pass with first-match-wins precedence,
 * avoiding cascade replacements where earlier relocations get re-relocated by subsequent rules.
 */
internal fun Iterable<Relocator>.remapSource(sourceContent: String): String {
  val relocatorList = this.toList()
  if (relocatorList.isEmpty() || sourceContent.isEmpty()) return sourceContent

  val simpleRelocators =
    relocatorList.filterIsInstance<SimpleRelocator>().filter {
      !it.rawString && it.pattern.isNotEmpty()
    }
  val otherRelocators = relocatorList.filter { it !in simpleRelocators }

  if (simpleRelocators.isEmpty()) {
    var content = sourceContent
    for (relocator in relocatorList) {
      content = relocator.applyToSourceContent(content)
    }
    return content
  }

  val patterns =
    simpleRelocators
      .flatMap { listOf(it.pattern, it.pathPattern) }
      .filter { it.isNotEmpty() }
      .distinct()
      .sortedByDescending { it.length }

  if (patterns.isEmpty()) {
    var content = sourceContent
    for (relocator in otherRelocators) {
      content = relocator.applyToSourceContent(content)
    }
    return content
  }

  val patternRegex = Regex("\\b(" + patterns.joinToString("|") { Regex.escape(it) } + ")\\b")

  val result = StringBuilder((sourceContent.length * 1.1).toInt())
  var lastIndex = 0

  for (match in patternRegex.findAll(sourceContent)) {
    val matchStart = match.range.first
    val matchEnd = match.range.last + 1
    val matchedText = match.value

    result.append(sourceContent, lastIndex, matchStart)
    lastIndex = matchEnd

    val contextValid = isSourceContextValid(matchedText, sourceContent, matchStart, matchEnd)

    var replaced = false
    if (contextValid) {
      for (relocator in relocatorList) {
        if (
          relocator is SimpleRelocator && !relocator.rawString && relocator.pattern.isNotEmpty()
        ) {
          val isDotMatch = matchedText == relocator.pattern
          val isPathMatch = matchedText == relocator.pathPattern
          if (isDotMatch || isPathMatch) {
            val sourceIncludes = getSourceSubpatterns(relocator.includes, relocator.pattern)
            val sourceExcludes = getSourceSubpatterns(relocator.excludes, relocator.pattern)
            val hasIncludes = relocator.includes.isNotEmpty()
            if (hasIncludes && sourceIncludes.isEmpty()) {
              continue
            }
            val isIncluded =
              !hasIncludes || sourceIncludes.any { matchesSubpattern(sourceContent, matchEnd, it) }
            val isExcluded = sourceExcludes.any { matchesSubpattern(sourceContent, matchEnd, it) }
            if (isIncluded && !isExcluded) {
              result.append(
                if (isDotMatch) relocator.shadedPattern else relocator.shadedPathPattern
              )
              replaced = true
              break
            }
          }
        }
      }
    }

    if (!replaced) {
      result.append(matchedText)
    }
  }

  result.append(sourceContent, lastIndex, sourceContent.length)
  if (otherRelocators.isEmpty()) {
    return result.toString()
  }

  var content = result.toString()
  for (relocator in otherRelocators) {
    content = relocator.applyToSourceContent(content)
  }
  return content
}

/**
 * Relocates a source file path by stripping its extension before matching against class/path
 * relocators, ensuring class-level include/exclude patterns work symmetrically with binary classes.
 */
internal fun Iterable<Relocator>.relocateSourcePath(path: String): String {
  if (isSourceFile(path)) {
    val extension = path.substringAfterLast('.', "")
    val pathWithoutExt = path.removeSuffix(".$extension")
    val className = pathWithoutExt.replace('/', '.')

    for (relocator in this) {
      val relocatedWithoutExt =
        when {
          relocator.canRelocatePath(pathWithoutExt) ->
            relocator.relocatePath(RelocatePathContext(pathWithoutExt))
          relocator.canRelocateClass(className) ->
            relocator.relocateClass(RelocateClassContext(className)).replace('.', '/')
          else -> continue
        }
      return "$relocatedWithoutExt.$extension"
    }
    return path
  }

  return relocatePath(path)
}

internal fun isSourceFile(path: String): Boolean {
  return path.endsWith(".java") ||
    path.endsWith(".kt") ||
    path.endsWith(".groovy") ||
    path.endsWith(".scala")
}

private fun getSourceSubpatterns(patterns: Set<String>, patternPrefix: String): Set<String> {
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

private fun matchesSubpattern(
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

/**
 * Determines whether a match in the source code represents a valid package/class reference rather
 * than a subpackage of a longer package, a subpath of a longer path, or an unrelated local
 * identifier (e.g., variable or parameter name).
 */
private fun isSourceContextValid(
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
      // A dot is only a package separator if it's not a Kotlin range '..' or varargs/spread '...'
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
