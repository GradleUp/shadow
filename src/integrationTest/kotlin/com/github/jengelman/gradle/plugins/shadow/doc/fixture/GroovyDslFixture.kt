package com.github.jengelman.gradle.plugins.shadow.doc.fixture

class GroovyDslFixture : GroovyScriptFixture() {

  override fun pre(): String {
    return """
      plugins {
        id("java")
        id("com.gradleup.shadow")
        id("application")
      }

      version = "1.0"
      group = "shadow"

      repositories {
        mavenLocal()
        mavenCentral()
      }
    """.trimIndent()
  }

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
        scriptMinusImports.toString(),
      )
    }
  }
}
