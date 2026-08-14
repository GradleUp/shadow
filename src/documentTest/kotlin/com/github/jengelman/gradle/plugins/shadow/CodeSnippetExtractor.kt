package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.DocumentTestBuildConfig.DOCS_DIR
import java.util.regex.Pattern
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

private val docRoot = Path(DOCS_DIR)

fun DslLang.extractCodeSnippets(): List<SnippetExecutable> {
  val lang = this
  return docRoot
    .walk()
    .filter { it.name.endsWith(".md", ignoreCase = true) }
    .flatMap { path ->
      val source = path.readText()
      val matcher = Pattern.compile("(?ims) {4}```${lang}\n(.*?)\n {4}```").matcher(source)

      buildList {
        while (matcher.find()) {
          val lineNumber = source.lineNumberAt(matcher.start())
          add(
            SnippetExecutable.create(
              lang = lang,
              snippet = matcher.group(1),
              testName = "${path.relativeTo(docRoot)}:$lineNumber",
              sourceLocation = "${path.toUri()}:$lineNumber",
            )
          )
        }
      }
    }
    .toList()
}

private fun String.lineNumberAt(index: Int): Int {
  var line = 1
  for (i in 0 until index.coerceAtMost(length)) {
    if (this[i] == '\n') line++
  }
  return line
}
