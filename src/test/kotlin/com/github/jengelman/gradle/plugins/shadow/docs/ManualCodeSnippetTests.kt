package com.github.jengelman.gradle.plugins.shadow.docs

import com.github.jengelman.gradle.plugins.shadow.docs.extractor.ManualSnippetExtractor
import com.github.jengelman.gradle.plugins.shadow.docs.fixture.GroovyDslFixture
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.TestCodeSnippet
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer.GradleBuildExecutor
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer.NoopExecutor
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.fixture.GroovyScriptFixture
import com.google.common.base.StandardSystemProperty
import java.io.File
import java.nio.file.Path
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir

class ManualCodeSnippetTests {

  @TestFactory
  fun provideDynamicTests(@TempDir tempDir: Path): List<DynamicTest> {
    val cwd = File(requireNotNull(StandardSystemProperty.USER_DIR.value()))
    val content = File(cwd, "src/docs")
    val snippets = mutableListOf<TestCodeSnippet>()

    fixtures.forEach { (selector, executor) ->
      ManualSnippetExtractor.extract(tempDir, content, selector, executor).forEach {
        snippets.add(it)
      }
    }

    return snippets.map {
      DynamicTest.dynamicTest(it.testName) { it.execute() }
    }
  }

  private companion object {
    private val fixtures = mapOf(
      "groovy" to GradleBuildExecutor(
        "build.gradle",
        GroovyDslFixture(),
        GroovyDslFixture.ImportsExtractor(),
      ),
      "groovy no-plugins" to GradleBuildExecutor(
        "build.gradle",
        GroovyScriptFixture(),
        GroovyDslFixture.ImportsExtractor(),
      ),
      "groovy no-run" to NoopExecutor,
    )
  }
}
