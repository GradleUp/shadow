package com.github.jengelman.gradle.plugins.shadow.doc.extractor

import com.github.jengelman.gradle.plugins.shadow.doc.fixture.SnippetFixture
import java.nio.file.Path

object NoopExecutor : SnippetExecutor {
  override val fixture: SnippetFixture get() = error("NoopExecutor does not have a fixture.")
  override fun execute(tempDir: Path, snippet: String) = Unit
}
