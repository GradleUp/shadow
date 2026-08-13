package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.snippet.CodeSnippetExtractor
import com.github.jengelman.gradle.plugins.shadow.snippet.DslLang
import com.github.jengelman.gradle.plugins.shadow.snippet.SnippetExecutable
import java.nio.file.Path
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class DocCodeSnippetTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("snippets")
  fun test(executable: SnippetExecutable, @TempDir tempDir: Path) {
    executable.tempDir = tempDir
    executable.execute()
  }

  companion object {
    @JvmStatic
    fun snippets(): List<SnippetExecutable> {
      val langExecutables = DslLang.entries.map { executor -> CodeSnippetExtractor.extract(executor) }

      check(langExecutables.sumOf { it.size } > 0) { "No code snippets found." }
      check(langExecutables.size == DslLang.entries.size) {
        "We must provide build script snippets for all languages."
      }
      check(langExecutables.map { it.size }.distinct().size == 1) {
        "All languages must have the same number of code snippets."
      }

      return langExecutables.flatten()
    }
  }
}
