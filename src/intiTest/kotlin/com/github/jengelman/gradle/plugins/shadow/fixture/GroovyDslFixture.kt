package com.github.jengelman.gradle.plugins.shadow.fixture

object GroovyDslFixture : SnippetFixture {

  override val pre: String = """
    plugins {
      id 'java'
      id 'com.gradleup.shadow'
    }
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
