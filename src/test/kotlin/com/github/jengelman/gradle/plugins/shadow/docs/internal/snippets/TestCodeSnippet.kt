package com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer.ExceptionTransformer
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer.SnippetExecutor
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
      executor.execute(tempDir.toFile(), this)
    } catch (t: Throwable) {
      throw exceptionTransformer.transform(t, requireNotNull(executor.fixture).offset)
    }
  }
}
