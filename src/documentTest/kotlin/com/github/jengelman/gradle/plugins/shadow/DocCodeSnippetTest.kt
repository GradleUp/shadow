package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.snippet.CodeSnippetExtractor
import com.github.jengelman.gradle.plugins.shadow.snippet.DslLang
import java.nio.file.Path
import kotlin.io.path.createDirectory
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir

class DocCodeSnippetTest {

  @TestFactory
  fun provideDynamicTests(@TempDir root: Path): List<DynamicTest> {
    val langExecutables = DslLang.entries.map { executor -> CodeSnippetExtractor.extract(executor) }

    check(langExecutables.sumOf { it.size } > 0) { "No code snippets found." }
    check(langExecutables.size == DslLang.entries.size) {
      "We must provide build script snippets for all languages."
    }
    check(langExecutables.map { it.size }.distinct().size == 1) {
      "All languages must have the same number of code snippets."
    }

    return langExecutables.flatten().map {
      val dirName = it.displayName.replace(nonAlphanumeric, "_")
      it.tempDir = root.resolve(dirName).createDirectory()
      DynamicTest.dynamicTest(it.displayName, it)
    }
  }
}

private val nonAlphanumeric = "[^a-zA-Z0-9]".toRegex()
