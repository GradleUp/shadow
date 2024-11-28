package com.github.jengelman.gradle.plugins.shadow.docs

import com.github.jengelman.gradle.plugins.shadow.docs.extractor.ManualSnippetExtractor
import com.github.jengelman.gradle.plugins.shadow.docs.fixture.GroovyDslFixture
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executor.GradleBuildExecutor
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executor.NoopExecutor
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.fixture.GroovyScriptFixture
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
      "groovy" to GradleBuildExecutor(
        "build.gradle",
        GroovyDslFixture(),
        GroovyDslFixture.importsExtractor,
      ),
      "groovy no-plugins" to GradleBuildExecutor(
        "build.gradle",
        GroovyScriptFixture(),
        GroovyDslFixture.importsExtractor,
      ),
      "groovy no-run" to NoopExecutor,
    )
  }
}
