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

  @OptIn(ExperimentalPathApi::class)
  private val markdownPaths = docRoot.walk()
    .filter { it.name.endsWith(".md", ignoreCase = true) }
    .toList()

  fun extract(lang: DslLang): List<SnippetExecutable> {
    val codeBlockPattern = Pattern.compile("(?ims)```${lang}\n(.*?)\n```")
    return markdownPaths.flatMap { path ->
      extractExecutables(lang, path, codeBlockPattern)
    }
  }

  private fun extractExecutables(
    lang: DslLang,
    markdownPath: Path,
    snippetBlockPattern: Pattern,
  ): List<SnippetExecutable> {
    val relativeDocPath = markdownPath.relativeTo(docRoot).pathString
    val snippetsByLine = findSnippetsByLine(markdownPath.readText(), snippetBlockPattern)
    return snippetsByLine.map { (lineNumber, snippet) ->
      SnippetExecutable.create(
        lang,
        snippet,
        "$relativeDocPath:$lineNumber",
      ) {
        RuntimeException("The error line in the doc is near ${markdownPath.toUri()}:$lineNumber", it)
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
      snippetBlocksByLine[lineNumber] = block.substring(block.indexOf("\n") + 1, block.lastIndexOf("\n"))
      codeIndex += block.length
    }

    return snippetBlocksByLine
  }
}
