package com.github.jengelman.gradle.plugins.shadow.doc.extractor

import com.github.jengelman.gradle.plugins.shadow.doc.fixture.SnippetFixture
import java.nio.file.Path

interface SnippetExecutor {
  val fixture: SnippetFixture

  fun execute(tempDir: Path, snippet: String)
}