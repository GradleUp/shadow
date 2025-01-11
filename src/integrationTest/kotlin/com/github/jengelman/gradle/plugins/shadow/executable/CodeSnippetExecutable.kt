package com.github.jengelman.gradle.plugins.shadow.executable

import com.github.jengelman.gradle.plugins.shadow.executor.SnippetExecutor
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.function.Executable

class CodeSnippetExecutable(
  private val root: Path,
  private val snippet: String,
  val testName: String,
  private val executor: SnippetExecutor,
  private val exceptionTransformer: (Throwable) -> Throwable,
) : Executable {
  override fun execute() {
    try {
      executor.execute(createTempDirectory(root), snippet)
    } catch (t: Throwable) {
      throw exceptionTransformer(t)
    }
  }
}
