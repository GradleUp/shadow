package com.github.jengelman.gradle.plugins.shadow.snippet

import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

object CodeSnippetExtractor {
  private val docRoot = Path(System.getProperty("DOCS_DIR"))

  fun extract(
    lang: DslLang,
  ): List<SnippetExecutable> {
    val snippets = mutableListOf<SnippetExecutable>()
    val snippetBlockPattern = Pattern.compile("(?ims) {4}```${lang}\n(.*?)\n {4}```")
    @OptIn(ExperimentalPathApi::class)
    docRoot.walk()
      .filter { it.name.endsWith(".md", ignoreCase = true) }
      .forEach {
        addSnippets(snippets, it, snippetBlockPattern, lang)
      }
    return snippets
  }

  private fun addSnippets(
    snippets: MutableList<SnippetExecutable>,
    sourcePath: Path,
    snippetBlockPattern: Pattern,
    lang: DslLang,
  ) {
    val source = sourcePath.readText()
    val relativeDocPath = sourcePath.relativeTo(docRoot).pathString
    val snippetsByLine = findSnippetsByLine(source, snippetBlockPattern)

    snippets += snippetsByLine.map { (lineNumber, snippet) ->
      SnippetExecutable.create(
        lang,
        snippet,
        "$relativeDocPath:$lineNumber",
      ) {
        val message = "The error line in the doc is near ${sourcePath.toUri()}:$lineNumber"
        RuntimeException(message, it)
      }
    }
  }

  private fun findSnippetBlocks(code: String, snippetTagPattern: Pattern) = buildList {
    val matcher = snippetTagPattern.matcher(code)
    while (matcher.find()) {
      add(matcher.group(0))
    }
  }

  private fun findSnippetsByLine(source: String, snippetTagPattern: Pattern): Map<Int, String> {
    val snippetBlocks = findSnippetBlocks(source, snippetTagPattern)
    val snippetBlocksByLine = mutableMapOf<Int, String>()

    var codeIndex = 0
    snippetBlocks.forEach { block ->
      codeIndex = source.indexOf(block, codeIndex)
      val lineNumber = source.substring(0, codeIndex).lines().size + 1
      snippetBlocksByLine[lineNumber] = extractSnippetFromBlock(block)
      codeIndex += block.length
    }

    return snippetBlocksByLine
  }

  private fun extractSnippetFromBlock(tag: String): String {
    return tag.substring(tag.indexOf("\n") + 1, tag.lastIndexOf("\n"))
  }
}
