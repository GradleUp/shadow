package com.github.jengelman.gradle.plugins.shadow.executable

import com.github.jengelman.gradle.plugins.shadow.DocCodeSnippetTest
import com.github.jengelman.gradle.plugins.shadow.executor.SnippetExecutor
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

object CodeSnippetExtractor {
  fun extract(
    root: Path,
    docRoot: Path,
    lang: String,
    executor: SnippetExecutor,
  ): List<CodeSnippetExecutable> {
    val snippets = mutableListOf<CodeSnippetExecutable>()
    val snippetBlockPattern = Pattern.compile("(?ims) {4}```$lang\n(.*?)\n {4}```")
    @OptIn(ExperimentalPathApi::class)
    docRoot.walk()
      .filter { it.name.endsWith(".md", ignoreCase = true) }
      .forEach {
        addSnippets(root, snippets, it, snippetBlockPattern, executor)
      }
    return snippets
  }

  private fun addSnippets(
    root: Path,
    snippets: MutableList<CodeSnippetExecutable>,
    path: Path,
    snippetBlockPattern: Pattern,
    executor: SnippetExecutor,
  ) {
    val source = path.readText()
    val relativeDocPath = path.relativeTo(DocCodeSnippetTest.docsDir).pathString
    val snippetsByLine = findSnippetsByLine(source, snippetBlockPattern)

    snippetsByLine.forEach { (lineNumber, snippet) ->
      snippets.add(createSnippet(root, relativeDocPath, path, lineNumber, snippet, executor))
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

  private fun createSnippet(
    root: Path,
    sourceClassName: String,
    sourcePath: Path,
    lineNumber: Int,
    snippet: String,
    executor: SnippetExecutor,
  ): CodeSnippetExecutable {
    return CodeSnippetExecutable(
      root,
      snippet,
      "$sourceClassName:$lineNumber",
      executor,
    ) {
      val message = "The error line in the doc is near ${sourcePath.toUri()}:$lineNumber"
      RuntimeException(message, it)
    }
  }
}
