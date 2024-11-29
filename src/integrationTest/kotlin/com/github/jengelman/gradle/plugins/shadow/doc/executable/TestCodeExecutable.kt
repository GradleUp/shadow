package com.github.jengelman.gradle.plugins.shadow.doc.executable

import com.github.jengelman.gradle.plugins.shadow.doc.exception.ExceptionTransformer
import com.github.jengelman.gradle.plugins.shadow.doc.extractor.SnippetExecutor
import java.nio.file.Path
import org.junit.jupiter.api.function.Executable

class TestCodeExecutable(
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
      throw exceptionTransformer.transform(t, executor.fixture.offset)
    }
  }
}
