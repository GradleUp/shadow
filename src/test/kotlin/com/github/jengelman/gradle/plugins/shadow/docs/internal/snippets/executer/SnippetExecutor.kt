package com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.executer

import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.TestCodeSnippet
import com.github.jengelman.gradle.plugins.shadow.docs.internal.snippets.fixture.SnippetFixture
import java.io.File

interface SnippetExecutor {
  val fixture: SnippetFixture?

  @Throws(Exception::class)
  fun execute(tempDir: File, snippet: TestCodeSnippet)
}
