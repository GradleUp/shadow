package com.github.jengelman.gradle.plugins.shadow.fixture

object GroovyDslFixture : SnippetFixture {

  override val pluginsBlock: String = """
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
      // Replace the version placeholder with a version that will pass validation.
      scriptMinusImports.toString().replace("<version>", "+"),
    )
  }
}
