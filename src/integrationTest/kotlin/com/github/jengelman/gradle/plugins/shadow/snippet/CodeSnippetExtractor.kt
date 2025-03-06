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
    return markdownPaths.flatMap { path ->
      createExecutables(lang, path)
    }
  }

  private fun createExecutables(
    lang: DslLang,
    markdownPath: Path,
  ): List<SnippetExecutable> {
    val relativeDocPath = markdownPath.relativeTo(docRoot).pathString
    return createSnippets(markdownPath.readText(), lang).map { (lineNumber, snippet) ->
      SnippetExecutable.create(
        lang,
        snippet,
        "$relativeDocPath:$lineNumber",
      ) {
        RuntimeException("The error line in the doc is near ${markdownPath.toUri()}:$lineNumber", it)
      }
    }
  }

  private fun createSnippets(source: String, lang: DslLang) = buildMap {
    val pattern = Pattern.compile("(?ims)```${lang}\n(.*?)\n```")
    val matcher = pattern.matcher(source)

    while (matcher.find()) {
      val line = source.lineNumberAt(matcher.start())
      val code = matcher.group(1)
      put(line, code)
    }
  }

  private fun String.lineNumberAt(index: Int): Int {
    var line = 1
    for (i in 0 until index.coerceAtMost(length)) {
      if (this[i] == '\n') line++
    }
    return line
  }
}
