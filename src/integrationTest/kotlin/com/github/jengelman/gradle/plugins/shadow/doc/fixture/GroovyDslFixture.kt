package com.github.jengelman.gradle.plugins.shadow.doc.fixture

import org.intellij.lang.annotations.Language

class GroovyDslFixture : SnippetFixture {

  override val pre: String = projectBuildText

  override val post: String = ""

  companion object {
    @Language("Groovy")
    private val projectBuildText = """
      plugins {
        id("java")
        id("com.gradleup.shadow")
        id("application")
      }
      repositories {
        mavenLocal()
        mavenCentral()
      }
      version = "1.0"
      group = "shadow"
    """.trimIndent()

    val importsExtractor: (String) -> List<String> = { snippet ->
      val imports = StringBuilder()
      val scriptMinusImports = StringBuilder()

      snippet.lines().forEach { line ->
        val target = if (line.trim().startsWith("import ")) imports else scriptMinusImports
        target.append(line).append("\n")
      }

      listOf(
        imports.toString(),
        scriptMinusImports.toString(),
      )
    }
  }
}
