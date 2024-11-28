package com.github.jengelman.gradle.plugins.shadow.docs.extractor

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.TestCodeSnippet
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executor.ExceptionTransformer
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executor.SnippetExecutor
import java.io.File
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.io.path.listDirectoryEntries

object ManualSnippetExtractor {
  @JvmStatic
  fun extract(
    tempDir: Path,
    root: File,
    cssClass: String,
    executor: SnippetExecutor,
  ): List<TestCodeSnippet> {
    val snippets = mutableListOf<TestCodeSnippet>()
    val snippetBlockPattern = Pattern.compile("(?ims)```$cssClass\n(.*?)\n```")
    root.toPath().listDirectoryEntries("**/*.md").forEach {
      addSnippets(tempDir, snippets, it.toFile(), snippetBlockPattern, executor)
    }
    return snippets
  }

  private fun addSnippets(
    tempDir: Path,
    snippets: MutableList<TestCodeSnippet>,
    file: File,
    snippetBlockPattern: Pattern,
    executor: SnippetExecutor,
  ) {
    val source = file.readText()
    val testName = "${file.parentFile.name}/${file.name}"
    val snippetsByLine = findSnippetsByLine(source, snippetBlockPattern)

    snippetsByLine.forEach { (lineNumber, snippet) ->
      snippets.add(createSnippet(tempDir, testName, file, lineNumber, snippet, executor))
    }
  }

  private fun findSnippetBlocks(code: String, snippetTagPattern: Pattern): List<String> {
    val tags = mutableListOf<String>()
    val matcher = snippetTagPattern.matcher(code)
    while (matcher.find()) {
      tags.add(matcher.group(0))
    }
    return tags
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
    sourceFile: File,
    lineNumber: Int,
    snippet: String,
    executor: SnippetExecutor,
  ): TestCodeSnippet {
    return TestCodeSnippet(
      tempDir,
      snippet,
      "$sourceClassName:$lineNumber",
      executor,
      ExceptionTransformer(sourceClassName, sourceFile.name, lineNumber),
    )
  }
}
