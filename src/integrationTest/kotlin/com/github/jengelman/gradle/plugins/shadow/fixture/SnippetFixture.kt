package com.github.jengelman.gradle.plugins.shadow.fixture

interface SnippetFixture {
  val pluginsBlock: String

  companion object {
    val importsExtractor: (String) -> List<String> = { snippet ->
      val imports = StringBuilder()
      val scriptMinusImports = StringBuilder()

      snippet.lines().forEach { line ->
        val target = if (line.trim().startsWith("import ")) imports else scriptMinusImports
        target.append(line).append("\n")
      }

      listOf(
        imports.toString(),
        // Replace the version placeholders.
        scriptMinusImports.toString().replace("<version>", "+"),
      )
    }
  }
}
