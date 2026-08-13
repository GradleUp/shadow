package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.snippet.DslLang
import com.github.jengelman.gradle.plugins.shadow.snippet.SnippetExecutable
import com.github.jengelman.gradle.plugins.shadow.snippet.extractCodeSnippets
import java.nio.file.Path
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

class DocCodeSnippetTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("snippets")
  fun test(executable: SnippetExecutable, @TempDir tempDir: Path) {
    executable.execute(tempDir)
  }

  private companion object {
    @JvmStatic
    fun snippets(): List<Arguments> {
      val langExecutables = DslLang.entries.map { executor -> extractCodeSnippets(executor) }

      check(langExecutables.sumOf { it.size } > 0) { "No code snippets found." }
      check(langExecutables.map { it.size }.distinct().size == 1) {
        "All languages must have the same number of code snippets."
      }

      return langExecutables.flatten().map { executable ->
        arguments(named(executable.displayName, executable))
      }
    }
  }
}
