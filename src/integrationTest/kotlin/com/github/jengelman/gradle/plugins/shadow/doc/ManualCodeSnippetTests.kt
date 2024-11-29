package com.github.jengelman.gradle.plugins.shadow.doc

import com.github.jengelman.gradle.plugins.shadow.doc.extractor.GroovyBuildExecutor
import com.github.jengelman.gradle.plugins.shadow.doc.extractor.ManualSnippetExtractor
import com.github.jengelman.gradle.plugins.shadow.doc.extractor.NoopExecutor
import com.github.jengelman.gradle.plugins.shadow.doc.fixture.GroovyDslFixture
import java.nio.file.Path
import kotlin.io.path.Path
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir

class ManualCodeSnippetTests {

  @TestFactory
  fun provideDynamicTests(@TempDir tempDir: Path): List<DynamicTest> {
    return fixtures.flatMap { (selector, executor) ->
      ManualSnippetExtractor.extract(tempDir, Path(docsDir), selector, executor)
    }.map {
      DynamicTest.dynamicTest(it.testName, it)
    }
  }

  private companion object {
    private val docsDir = System.getProperty("DOCS_DIR")

    private val fixtures = mapOf(
      "groovy" to GroovyBuildExecutor(
        GroovyDslFixture(),
        GroovyDslFixture.importsExtractor,
      ),
      "groovy no-run" to NoopExecutor,
    )
  }
}
