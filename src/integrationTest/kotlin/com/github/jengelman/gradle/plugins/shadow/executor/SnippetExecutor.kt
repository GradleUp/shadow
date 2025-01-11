package com.github.jengelman.gradle.plugins.shadow.executor

import com.github.jengelman.gradle.plugins.shadow.fixture.SnippetFixture
import java.nio.file.Path

interface SnippetExecutor {
  val fixture: SnippetFixture

  fun execute(tempDir: Path, snippet: String)
}
