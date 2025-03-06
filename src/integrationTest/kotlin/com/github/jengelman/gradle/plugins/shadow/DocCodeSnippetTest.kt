package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.snippet.CodeSnippetExtractor
import com.github.jengelman.gradle.plugins.shadow.snippet.DslLang
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir

class DocCodeSnippetTest {

  @OptIn(ExperimentalStdlibApi::class)
  @TestFactory
  fun provideDynamicTests(@TempDir root: Path): List<DynamicTest> {
    val langExecutables = DslLang.entries.map { executor ->
      CodeSnippetExtractor.extract(executor)
    }

    check(langExecutables.sumOf { it.size } > 0) {
      "No code snippets found."
    }
    check(langExecutables.size == DslLang.entries.size) {
      "We must provide build script snippets for all languages."
    }
    check(langExecutables.map { it.size }.distinct().size == 1) {
      "All languages must have the same number of code snippets."
    }

    return langExecutables.flatten().map {
      // Create a temporary directory for each test, root will be deleted after all tests are run.
      it.tempDir = createTempDirectory(root)
      DynamicTest.dynamicTest(it.displayName, it)
    }
  }
}
