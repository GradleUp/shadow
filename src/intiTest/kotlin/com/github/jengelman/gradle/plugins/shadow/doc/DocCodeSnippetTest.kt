package com.github.jengelman.gradle.plugins.shadow.doc

import com.github.jengelman.gradle.plugins.shadow.doc.executable.CodeSnippetExtractor
import com.github.jengelman.gradle.plugins.shadow.doc.executor.GroovyBuildExecutor
import com.github.jengelman.gradle.plugins.shadow.doc.executor.NoopExecutor
import com.github.jengelman.gradle.plugins.shadow.doc.fixture.GroovyDslFixture
import java.nio.file.Path
import kotlin.io.path.Path
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir

class DocCodeSnippetTest {

  @TestFactory
  fun provideDynamicTests(@TempDir tempDir: Path): List<DynamicTest> {
    return fixtures.flatMap { (selector, executor) ->
      CodeSnippetExtractor.extract(tempDir, docsDir, selector, executor)
    }.map {
      DynamicTest.dynamicTest(it.testName, it)
    }
  }

  companion object {
    private val fixtures = mapOf(
      "groovy" to GroovyBuildExecutor(
        GroovyDslFixture,
        GroovyDslFixture.importsExtractor,
      ),
      "groovy no-run" to NoopExecutor,
    )

    val docsDir: Path = Path(System.getProperty("DOCS_DIR"))
  }
}
