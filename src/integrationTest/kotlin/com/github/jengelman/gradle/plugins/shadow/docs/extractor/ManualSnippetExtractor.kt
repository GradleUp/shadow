package com.github.jengelman.gradle.plugins.shadow.docs.extractor

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.TestCodeSnippet
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executor.ExceptionTransformer
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executor.SnippetExecutor
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk

object ManualSnippetExtractor {
  fun extract(
    tempDir: Path,
    docRoot: Path,
    cssClass: String,
    executor: SnippetExecutor,
  ): List<TestCodeSnippet> {
    val snippets = mutableListOf<TestCodeSnippet>()
    val snippetBlockPattern = Pattern.compile("(?ims)```$cssClass\n(.*?)\n```")
    @OptIn(ExperimentalPathApi::class)
    docRoot.walk()
      .filter { it.name.endsWith(".md", ignoreCase = true) }
      .forEach {
        addSnippets(tempDir, snippets, it, snippetBlockPattern, executor)
      }
    return snippets
  }

  private fun addSnippets(
    tempDir: Path,
    snippets: MutableList<TestCodeSnippet>,
    path: Path,
    snippetBlockPattern: Pattern,
    executor: SnippetExecutor,
  ) {
    val source = path.readText()
    val testName = "${path.parent.name}/${path.name}"
    val snippetsByLine = findSnippetsByLine(source, snippetBlockPattern)

    snippetsByLine.forEach { (lineNumber, snippet) ->
      snippets.add(createSnippet(tempDir, testName, path, lineNumber, snippet, executor))
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
      val lineNumber = source.substring(0, codeIndex).lines().size + 2
      snippetBlocksByLine[lineNumber] = extractSnippetFromBlock(block)
      codeIndex += block.length
    }

    return snippetBlocksByLine
  }

  private fun extractSnippetFromBlock(tag: String): String {
    return tag.substring(tag.indexOf("\n") + 1, tag.lastIndexOf("\n"))
  }

  private fun createSnippet(
    tempDir: Path,
    sourceClassName: String,
    sourcePath: Path,
    lineNumber: Int,
    snippet: String,
    executor: SnippetExecutor,
  ): TestCodeSnippet {
    return TestCodeSnippet(
      tempDir,
      snippet,
      "$sourceClassName:$lineNumber",
      executor,
      ExceptionTransformer(sourceClassName, sourcePath.name, lineNumber),
    )
  }
}
