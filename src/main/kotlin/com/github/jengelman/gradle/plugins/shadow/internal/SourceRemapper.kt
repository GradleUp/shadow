package com.github.jengelman.gradle.plugins.shadow.internal

import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocatePathContext
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.relocation.relocatePath

private val KEYWORDS =
  setOf(
    "import",
    "package",
    "public",
    "protected",
    "private",
    "static",
    "final",
    "synchronized",
    "abstract",
    "volatile",
    "transient",
    "native",
    "strictfp",
    "extends",
    "implements",
    "throws",
    "return",
    "new",
    "throw",
    "instanceof",
    "case",
    "default",
    "yield",
    "val",
    "var",
    "fun",
    "is",
    "as",
    "in",
  )

/**
 * Match
 * - certain Java keywords + space
 * - beginning of Javadoc link + optional line breaks and continuations with '*'
 * - (opening curly brace / opening parenthesis / comma / equals / semicolon) + space
 * - (closing curly brace / closing multi-line comment) + space
 *
 * at end of string
 */
private val RX_ENDS_WITH_JAVA_KEYWORD =
  listOf(
      "\\b(${KEYWORDS.joinToString("|")}) $",
      "\\{@link( \\*)* $",
      "([{}(=;,:<>?&|@\\[\\]]|\\*/) $",
    )
    .joinToString("|")
    .toPattern()

private val RX_ENDS_WITH_DOT_SLASH_SPACE = "[./ ]$".toPattern()

internal val RX_WHITESPACE = "\\s+".toRegex()

internal fun String.isJavaContextValid(): Boolean {
  if (!RX_ENDS_WITH_DOT_SLASH_SPACE.matcher(this).find()) {
    return true
  }
  if (endsWith(' ')) {
    var end = length - 1
    while (end > 0 && this[end - 1].isWhitespace()) {
      end--
    }
    var start = end
    while (start > 0 && this[start - 1].isJavaIdentifierPart()) {
      start--
    }
    if (start < end && substring(start, end) in KEYWORDS) {
      return true
    }
  }
  return RX_ENDS_WITH_JAVA_KEYWORD.matcher(this).find()
}

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

    var cursor = matchStart - 1
    while (cursor >= 0 && sourceContent[cursor].isWhitespace()) {
      cursor--
    }
    val lookbackStart = (cursor - 64).coerceAtLeast(0)
    val previousSnippetOneLine =
      sourceContent.substring(lookbackStart, matchStart).replace(RX_WHITESPACE, " ")
    val contextValid = previousSnippetOneLine.isJavaContextValid()

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

internal fun getSourceSubpatterns(patterns: Set<String>, patternPrefix: String): Set<String> {
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

internal fun matchesSubpattern(
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
