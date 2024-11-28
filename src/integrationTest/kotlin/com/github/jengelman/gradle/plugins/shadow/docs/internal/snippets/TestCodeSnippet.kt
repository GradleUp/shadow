package com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executor.ExceptionTransformer
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executor.SnippetExecutor
import java.nio.file.Path
import org.junit.jupiter.api.function.Executable

class TestCodeSnippet(
  private val tempDir: Path,
  val snippet: String,
  val testName: String,
  private val executor: SnippetExecutor,
  private val exceptionTransformer: ExceptionTransformer,
) : Executable {
  override fun execute() {
    try {
      executor.execute(tempDir, this)
    } catch (t: Throwable) {
      throw exceptionTransformer.transform(t, requireNotNull(executor.fixture).offset)
    }
  }
}
