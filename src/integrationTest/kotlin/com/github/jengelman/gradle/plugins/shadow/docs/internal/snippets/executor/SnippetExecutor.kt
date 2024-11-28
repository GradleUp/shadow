package com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executor

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.TestCodeSnippet
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.fixture.SnippetFixture
import java.nio.file.Path

interface SnippetExecutor {
  val fixture: SnippetFixture

  fun execute(tempDir: Path, snippet: TestCodeSnippet)
}

object NoopExecutor : SnippetExecutor {
  override val fixture: SnippetFixture get() = error("NoopExecutor does not have a fixture.")
  override fun execute(tempDir: Path, snippet: TestCodeSnippet) = Unit
}
