package com.github.jengelman.gradle.plugins.shadow.executor

import com.github.jengelman.gradle.plugins.shadow.fixture.SnippetFixture
import java.nio.file.Path

object NoopExecutor : SnippetExecutor {
  override val fixture: SnippetFixture get() = error("NoopExecutor does not have a fixture.")
  override fun execute(tempDir: Path, snippet: String) = Unit
}
