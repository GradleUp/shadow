package com.github.jengelman.gradle.plugins.shadow.doc.executable

import com.github.jengelman.gradle.plugins.shadow.doc.executor.SnippetExecutor
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.function.Executable

class CodeSnippetExecutable(
  private val tempDir: Path,
  private val snippet: String,
  val testName: String,
  private val executor: SnippetExecutor,
  private val exceptionTransformer: (Throwable) -> Throwable,
) : Executable {
  override fun execute() {
    try {
      // TODO: any way to createTempDirectory with `@TempDir` for each `Executable`?
      executor.execute(createTempDirectory(tempDir, "doc-"), snippet)
    } catch (t: Throwable) {
      throw exceptionTransformer(t)
    }
  }
}
